# Androcar_Gps — notas de desenvolvimento

App Android que envia o GPS do celular para o tablet (Google Maps via local fictício).
Desenvolvedor: Heitor Campos (GitHub heitorcampos2).

## No PC

```
C:\github\Androcar_Gps\
  codigo-github\   este repositório (github.com/heitorcampos2/Androcar_Gps)
  versoes\x.y.z\   APK de cada versão
```

## Versões e atualização (igual ao Spectra)

- Cada push no `main` roda o GitHub Actions (`.github/workflows/build.yml`), compila e publica a release `v1.0.<n>`.
- `versionCode = 100 + n`; o app consulta a release mais recente e oferece a atualização.
- Download fixo: `https://github.com/heitorcampos2/Androcar_Gps/releases/latest/download/Androcar_Gps.apk`
- Chave de assinatura própria em `keystore/androcar.jks` (mesma no PC e no GitHub). Não troque: senão a atualização falha nos aparelhos.
- Build local: `.\gradlew.bat assembleRelease` → `app\build\outputs\apk\release\` (versão 1.0.0).

## Código

- `service/SenderService.kt` — celular: GPS (FusedLocation, 1 s) + servidor TCP na porta 47800
- `service/ReceiverService.kt` — tablet: conecta no celular (gateway do Wi-Fi ou IP manual), reconecta sozinho
- `service/MockLocation.kt` — tablet: aplica a posição como local fictício (GPS, rede e Google Play)
- `service/GpsTileService.kt` — botão nas configurações rápidas
- `Updater.kt` — release mais recente do GitHub → baixa e instala
- `Protocol.kt` — `HELLO <código>` → `OK`, depois uma posição JSON por linha e `P` (sinal de vida) a cada 2 s
