#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
inicio=$SECONDS
etapa="construção"
trap 'codigo=$?; if ((codigo != 0)); then printf "\nFALHA na etapa: %s (código %d).\n" "$etapa" "$codigo"; fi' EXIT
docker compose up -d --build
etapa="integração"
docker compose run --rm -T --interactive=false executor testar
printf '\nTESTES CONCLUÍDOS em %d segundos.\n' "$((SECONDS-inicio))"
printf 'Conferidos: integridade, concorrência, BitTorrent entre peers e limpeza.\n'
printf 'Os testes unitários de interrupção, timeout e corrupção passaram na construção.\n'
printf 'Serviços permanecem ligados; execute docker compose down para encerrar.\n'
