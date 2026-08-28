# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Étape 1 — build : bundle Scala.js optimisé + distribution serveur
# ---------------------------------------------------------------------------
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.6_7_1.10.7_3.3.5 AS build

WORKDIR /build

# Les définitions de build d'abord : tant qu'elles ne changent pas, le
# téléchargement des dépendances reste dans le cache de couches.
COPY project/build.properties project/plugins.sbt ./project/
COPY build.sbt ./
RUN --mount=type=cache,target=/root/.cache/coursier \
    --mount=type=cache,target=/root/.sbt \
    sbt update

COPY modules ./modules

# `fullLinkJS` écrit dans static/js (voir jsOutputDir), `Universal/stage` produit
# la distribution serveur dans modules/server/target/universal/stage.
RUN --mount=type=cache,target=/root/.cache/coursier \
    --mount=type=cache,target=/root/.sbt \
    sbt "client/fullLinkJS" "server/Universal/stage"

# ---------------------------------------------------------------------------
# Étape 2 — exécution : JRE seule, sans sbt ni sources
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Le serveur résout `data/` et `static/` relativement au répertoire courant.
WORKDIR /app

COPY --from=build /build/modules/server/target/universal/stage /app/server
COPY --from=build /build/static/js /app/static/js
COPY static/index.html /app/static/index.html
COPY static/css /app/static/css
COPY data/communes.parquet data/fichier_professionnels_avec_coords.parquet /app/data/

RUN useradd --system --uid 10001 healthmap \
 && mkdir -p /app/data/geo \
 && chown -R healthmap:healthmap /app
USER healthmap

EXPOSE 8080

# Les deux arguments sont l'hôte et le port de bind (voir Main.bindHost/bindPort).
ENTRYPOINT ["/app/server/bin/healthmap-server"]
CMD ["0.0.0.0", "8080"]
