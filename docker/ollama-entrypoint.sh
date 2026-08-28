#!/bin/sh
# Démarre Ollama puis récupère le modèle demandé avant de laisser la main.
#
# Le `pull` a besoin du serveur : on le lance en arrière-plan, on attend qu'il
# réponde, on télécharge le modèle une fois pour toutes (le volume `ollama`
# conserve les poids d'un `up` à l'autre), puis le processus serveur redevient
# le processus principal du conteneur.
set -e

MODEL="${OLLAMA_MODEL:-qwen3:8b}"

ollama serve &
SERVE_PID=$!

echo "Ollama : attente du serveur…"
i=0
until ollama list >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "Ollama : le serveur n'a pas démarré en 60 s." >&2
    exit 1
  fi
  sleep 1
done

if ollama list | awk 'NR > 1 { print $1 }' | grep -qx "$MODEL"; then
  echo "Ollama : modèle $MODEL déjà présent."
else
  echo "Ollama : téléchargement de $MODEL (environ 5 Go, seulement au premier démarrage)…"
  ollama pull "$MODEL"
fi

echo "Ollama : prêt, modèle $MODEL disponible."
wait "$SERVE_PID"
