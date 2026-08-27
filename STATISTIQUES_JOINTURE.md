# Statistiques de jointure professionnels ↔ communes

Mesuré le 2026-08-27 par `ProfessionalRepository.load`. Régénéré par
`sbt "server/runMain fr.ipssi.healthmap.server.data.pipeline.GenerateJoinReport"`
après toute mise à jour des Parquet sources — ne pas éditer à la main.

## Grain résolu (432 015 professionnels avec code postal et commune, GPS ou non)

Alimente `professions`, `total`, région, département, couverture, densité,
après exclusion des non-résolus (429 998 professionnels).

| | Valeur | % |
| --- | ---: | ---: |
| Total | 432 015 | 100,0 % |
| Correspondance exacte | 410 039 | 94,9 % |
| Repli (code postal seul) | 19 959 | 4,6 % |
| Non résolu | 2 017 | 0,5 % |

## Grain géolocalisé (353 414 professionnels avec coordonnées GPS)

Alimente la carte (`/api/map`) et le classement des communes
(`/api/top-communes`) — sous-ensemble du grain résolu.

| | Valeur | % |
| --- | ---: | ---: |
| Total | 353 414 | 100,0 % |
| Correspondance exacte | 341 234 | 96,6 % |
| Repli (code postal seul) | 12 180 | 3,4 % |
| Non résolu | 0 | 0,0 % |
