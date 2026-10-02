#!/usr/bin/env bash
# Деплой указанной версии приложения: bash deploy.sh <полный SHA коммита>
set -euo pipefail

cd /opt/marketplace

TAG="${1:-}"
if ! [[ "$TAG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Usage: bash deploy.sh <40-char commit SHA>, got: '$TAG'" >&2
  exit 1
fi

# 1. Бэкап базы перед деплоем: миграцию Flyway откатить нельзя, а дамп можно восстановить
mkdir -p backups
BACKUP="backups/marketplace-$(date +%Y%m%d-%H%M%S)-${TAG:0:7}.dump"
docker compose exec -T postgres pg_dump -U marketplace -d marketplace -Fc > "$BACKUP"
echo "Backup: $BACKUP"

# храним только 7 последних дампов
ls -1t backups/*.dump | tail -n +8 | xargs -r rm --

# 2. Новая версия — в .env, чтобы её же подняли reboot и ручной docker compose up
sed -i "s/^APP_TAG=.*/APP_TAG=${TAG}/" .env

# 3. Скачиваем образ, пока старая версия ещё работает; только app, чтобы не перезапустить Postgres
docker compose pull app

# 4. Пересоздаётся только то, что изменилось (контейнер app)
docker compose up -d

# 5. Ждём health до ~90 секунд
for i in $(seq 1 30); do
  if docker run --rm --network marketplace_default curlimages/curl -fsS http://app:8080/actuator/health > /dev/null 2>&1; then
    echo "Deployed ${TAG}: app is healthy"
    exit 0
  fi
  sleep 2
done

echo "App did not become healthy, last logs:" >&2
docker compose logs app --tail 50 >&2
exit 1
