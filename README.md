# HealthMap — déserts médicaux (Scala)

## Etudiant - Groupe 1
- Amaury TISSOT
- Léa DRUFFIN
- Adrien FOUQUET
- Satya MINGUEZ
- Frédéric FERNANDES DA COSTA

## Présentation du projet

Portage en Scala 3 d'une application de cartographie de la répartition des professionnels de santé en France, à partir d'un jeu de 432 015 professionnels géolocalisés.

## Démarrage avec Docker

Seule voie qui livre l'assistant complet : un conteneur Ollama embarque le modèle
`qwen3:8b` et l'expose au serveur.

Prérequis : Docker Desktop, et pour l'accélération GPU une carte NVIDIA avec les
pilotes à jour (le NVIDIA Container Toolkit est intégré à Docker Desktop sous
Windows via WSL 2 ; sous Linux il s'installe séparément).

```bash
docker compose up -d
```

L'application est servie sur <http://localhost:8080>.

Le premier lancement construit l'image (compilation Scala.js + serveur, plusieurs
minutes) puis télécharge les ~5 Go du modèle en arrière-plan. La carte et les
statistiques sont disponibles immédiatement ; tant que le modèle n'est pas
téléchargé, l'assistant répond via l'orientation par référentiel, précédée d'un
avertissement. Suivre l'avancement du téléchargement :

```bash
docker compose logs -f ollama
```

Vérifier que le modèle tourne bien sur le GPU — la colonne `PROCESSOR` doit
indiquer `100% GPU` :

```bash
docker compose exec ollama ollama ps
```

Sans GPU exploitable, commenter le bloc `deploy:` du service `ollama` dans
`docker-compose.yml` : Ollama bascule sur le CPU, plus lent mais fonctionnel.

Arrêt, et remise à zéro complète (y compris les 5 Go du modèle) :

```bash
docker compose down
docker compose down -v
```

Les réglages de l'assistant sont passés au serveur par variables d'environnement
(`HEALTHMAP_OLLAMA_URL`, `HEALTHMAP_OLLAMA_MODEL`, `HEALTHMAP_OLLAMA_TIMEOUT_S`) ;
changer de modèle se fait en éditant `OLLAMA_MODEL`, `HEALTHMAP_OLLAMA_MODEL` et
le test du `healthcheck` dans `docker-compose.yml`.

## Démarrage en local (sbt)

Prérequis : JDK 21 et sbt (`winget install EclipseAdoptium.Temurin.21.JDK sbt.sbt`),
Node pour l'édition de liens Scala.js.

```bash
sbt dev          # compile le client puis démarre le serveur
```

L'application est servie sur <http://localhost:8080>. L'assistant suppose un
Ollama joignable sur `http://localhost:11434` ; à défaut il se replie sur
l'orientation par référentiel.

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

> **Deux totaux, à ne pas confondre** (voir la doc de tête de `ProfessionalRepository`) :
> - grain résolu (429 998 professionnels rattachés à une commune, GPS ou non) — `/api/professions`, `/api/regions`, `/api/departements`, `/api/coverage/:dept`
> - grain géolocalisé (353 414, GPS non nul, sous-ensemble du premier) — `/api/map`, `/api/top-communes`

## Régénérer data/communes.parquet

`data/communes.parquet` (quelques Mo) est versionné dans Git ; sa source,
`data/raw/communes-france-avec-polygon-2025.json` (référentiel des communes
françaises enrichi — population, département, région, grille de densité,
codes postaux — avec polygones, ~62 Mo), ne l'est pas (`.gitignore`,
`data/raw/`) : à déposer à ce chemin avant de régénérer le Parquet.

```bash
sbt "server/runMain fr.ipssi.healthmap.server.data.pipeline.ConvertCommunes"
```

La commande imprime des diagnostics de sanité (nombre de communes, codes
postaux distincts, communes corses, population totale) en face des valeurs
attendues, avant d'écraser `data/communes.parquet`.

## Régénérer STATISTIQUES_JOINTURE.md

```bash
sbt "server/runMain fr.ipssi.healthmap.server.data.pipeline.GenerateJoinReport"
```

Écrit `STATISTIQUES_JOINTURE.md` à la racine (versionné, comme les Parquet) :
qualité de la jointure professionnels ↔ communes — total, correspondance
exacte, repli, non résolu, en valeur et en pourcentage — sur le grain résolu
(432 015) et sur le grain géolocalisé (353 414). À rejouer après une mise à
jour de `data/fichier_professionnels_avec_coords.parquet` ou
`data/communes.parquet`, pas à chaque exécution du serveur : sinon seule la
date de mesure change dans le diff, pour rien.

## État

Lots A (socle et build), B (données DuckDB) et C (modèles partagés et API)
livrés. La source de données est `ProfessionalRepository` (DuckDB), branchée
dans `Main` — `data/fichier_professionnels_avec_coords.parquet` et
`data/communes.parquet`. Le lot E remplace `ChatService.rulesBased` par le
client Ollama.

## Écarts assumés avec la version Python

| Correction | Motif |
| --- | --- |
| Corse rattachée à `2A` / `2B` selon le seuil 20200 | le Python testait `startswith("2A")` sur des codes postaux numériques : la Corse n'apparaissait jamais |
| Département `79` renommé « Deux-Sèvres » | il était libellé « Nièvre », doublon du `58` |
| Conversion code postal → département unique (`ref.Geo`) | le Python en avait deux implémentations contradictoires |
| GeoJSON téléchargés une fois et mis en cache | le Python rejouait un `requests.get` à chaque interaction |
| Agrégats calculés une fois au démarrage | le Python rechargeait le Parquet à chaque onglet |
| Arrondissements de Paris, Lyon et Marseille rattachés à leur `code_insee` réel (`arrondissementsPLM`) | le référentiel communes-france n'a qu'une ligne par ville, avec un code postal générique absent des données CNAM : sans ce complément d'espace de recherche, les 33 769 professionnels concernés disparaissaient des agrégats départementaux et régionaux |
| `professions`/`total`/région/département/couverture/densité découplés du GPS (429 998 professionnels résolus à une commune, contre 353 414 pour la carte et le classement des communes) | 97,4 % des 78 601 professionnels sans coordonnées GPS se résolvent tout de même à une commune via `(code_postal, nom)` ; les exclure des agrégats territoriaux aurait été une perte sèche sans rapport avec la qualité de la jointure |
