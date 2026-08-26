# HealthMap — déserts médicaux (Scala)

Portage en Scala 3 de l'application Python/Streamlit `open_data_health_map-` :
cartographie de la répartition des professionnels de santé en France, à partir
d'un jeu de 432 015 professionnels géolocalisés.

## Démarrage

Prérequis : JDK 21 et sbt (`winget install EclipseAdoptium.Temurin.21.JDK sbt.sbt`),
Node pour l'édition de liens Scala.js.

```bash
sbt dev          # compile le client puis démarre le serveur
```

L'application est servie sur <http://localhost:8080>.

Autres commandes :

```bash
sbt test                  # tests du module partagé (JVM + JS) et du serveur
sbt sharedJVM/test        # référentiels et codecs, côté JVM
sbt client/fastLinkJS     # bundle de développement dans static/js
sbt build                 # bundle optimisé + archive distribuable
```

### « sbt : commande introuvable » sous Windows

L'installateur ajoute `sbt` et le JDK au `PATH` machine, mais les processus déjà
lancés gardent leur ancien environnement : un terminal ouvert avant l'installation
ne verra pas `sbt`. Le rouvrir suffit dans la plupart des cas ; pour recharger le
`PATH` dans la session courante sans rien fermer :

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:PATH = [Environment]::GetEnvironmentVariable('PATH', 'Machine') + ';' +
            [Environment]::GetEnvironmentVariable('PATH', 'User')
```

Si `Get-Command sbt` reste muet après réouverture du terminal, l'installation n'a
pas touché au `PATH` : ajouter le dossier au `PATH` utilisateur, une fois pour
toutes (l'écriture en `ExpandString` préserve les `%USERPROFILE%` déjà présents).

```powershell
$bin = 'C:\Program Files (x86)\sbt\bin'
$path = (Get-Item HKCU:\Environment).GetValue('PATH', '', 'DoNotExpandEnvironmentNames')
if ($path -split ';' -notcontains $bin) {
  Set-ItemProperty HKCU:\Environment PATH "$path;$bin" -Type ExpandString
}
```

Le nouveau `PATH` n'est visible que par les terminaux ouverts ensuite.

## Architecture

```
build.sbt                 cross-project shared / server / client
project/plugins.sbt       sbt-scalajs, sbt-scalajs-crossproject, sbt-native-packager
data/                     parquet enrichi (versionné), fonds GeoJSON en cache (ignorés)
modules/
  shared/                 modèles, codecs upickle, référentiels — compilé JVM + JS
  server/                 http4s-ember, DuckDB JDBC, proxy GeoJSON, assistant
  client/                 Scala.js + Laminar, façades Leaflet / Chart.js
static/                   index.html, feuille de style, bundle JavaScript
```

Le module `shared` est le point de contact de tous les lots : modèles, codecs,
liste des endpoints (`api.Endpoints`), référentiel géographique (`ref.Geo`) et
référentiel d'orientation (`ref.Symptoms`). Toute évolution de sa signature est
annoncée à l'équipe avant d'être poussée sur `main`.

## API

| Endpoint | Rôle |
| --- | --- |
| `GET /api/professions` | référentiel des professions et leurs effectifs |
| `GET /api/map?professions=` | agrégat par code postal pour la couche de points |
| `GET /api/regions?professions=` | agrégat régional (choroplèthe) |
| `GET /api/departements?professions=` | agrégat départemental (choroplèthe, histogramme) |
| `GET /api/top-communes?professions=&limit=` | communes les mieux dotées |
| `GET /api/coverage/:dept` | analyse de couverture d'un département |
| `POST /api/chat` | assistant d'orientation |
| `GET /geo/:nom.geojson` | fonds cartographiques, téléchargés une fois puis servis en local |

Le paramètre `professions` est une liste séparée par des virgules ; absent, il
signifie « toutes professions ».

## État

Lots A (socle et build) et C (modèles partagés et API) livrés. La source de
données est encore l'échantillon en mémoire `InMemoryProfessionalSource.stub` :
le lot B la remplace par l'implémentation DuckDB sans toucher aux routes, et le
lot E remplace `ChatService.rulesBased` par le client Ollama.

## Écarts assumés avec la version Python

| Correction | Motif |
| --- | --- |
| Corse rattachée à `2A` / `2B` selon le seuil 20200 | le Python testait `startswith("2A")` sur des codes postaux numériques : la Corse n'apparaissait jamais |
| Département `79` renommé « Deux-Sèvres » | il était libellé « Nièvre », doublon du `58` |
| Conversion code postal → département unique (`ref.Geo`) | le Python en avait deux implémentations contradictoires |
| GeoJSON téléchargés une fois et mis en cache | le Python rejouait un `requests.get` à chaque interaction |
| Agrégats calculés une fois au démarrage | le Python rechargeait le Parquet à chaque onglet |

## Équipe

Adrien Fouquet · Amaury Tissot · Léa Druffin · Satya Minguez · Frédéric FERNANDES DA COSTA
