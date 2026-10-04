#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p resultados relatorio
inicio=$SECONDS
etapa="construção"
trap 'codigo=$?; if ((codigo != 0)); then printf "\nFALHA na etapa: %s (código %d). Resultados concluídos preservados.\n" "$etapa" "$codigo"; fi' EXIT
docker compose up -d --build
{
  date --iso-8601=seconds
  uname -srmo
  lscpu
  free -h
  df -h .
  docker version --format 'Docker Engine {{.Server.Version}}'
  docker compose version
} > resultados/host.txt
etapa="testes"
docker compose run --rm -T --interactive=false executor testar
etapa="benchmark"
docker compose run --rm -T --interactive=false executor benchmark --perfil completo
etapa="PDF"
docker compose run --rm -T --interactive=false executor relatorio
printf '\nEXPERIMENTO CONCLUÍDO em %d segundos.\n' "$((SECONDS-inicio))"
printf '144 execuções / 540 downloads; resultados em resultados/; PDF em relatorio/relatorio.pdf.\n'
printf 'Para encerrar: docker compose down\n'
