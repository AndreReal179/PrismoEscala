# Prismo Escala

App Android de calendário para escala 12x36 (e 24x48, 24x72, 6x1, 5x2 ou personalizada),
com serviços extras, consultas, ajustes de dia, histórico de escalas e notificações.

## Como gerar o APK (GitHub Actions)
1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta (inclusive `.github` e `escala.keystore`).
2. Abra a aba **Actions** → "Gerar APK" roda sozinho a cada push (ou clique em *Run workflow*).
3. Quando terminar, baixe o artefato **PrismoEscala-APK** → `PrismoEscala.apk`.

## Assinatura
`escala.keystore` (senha `prismoescala`, alias `escala`). Guarde esse arquivo: as próximas versões
precisam dele para instalar por cima sem perder os dados.

## Novas versões
Aumente `versionCode` e `versionName` em `app/build.gradle.kts`.

## Estrutura
- `app/src/main/assets/www/index.html` — toda a interface e a lógica da escala
- `MainActivity.kt` — WebView + ponte com o Android (permissões, backup, vibração)
- `Agendador.kt` — agenda as notificações com AlarmManager (funcionam com o app fechado)
- `BootReceiver.kt` — reagenda depois de reiniciar o celular
