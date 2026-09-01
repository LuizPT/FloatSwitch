# FloatSwitch

## Objectivo

Aplicação Android nativa que apresenta atalhos flutuantes persistentes sobre qualquer aplicação. Ao tocar num atalho, abre directamente a aplicação correspondente.

## Tecnologia

- Kotlin
- Android Views/XML
- Gradle Kotlin DSL
- Não utilizar Jetpack Compose
- Preferir APIs Android nativas e dependências mínimas
- Não adicionar Internet, publicidade, analytics, contas ou serviços externos

## Compatibilidade

- Manter minSdk 26
- Manter compileSdk e targetSdk existentes
- Dispositivo principal: Android 14/API 34
- Resolução principal: 1920x720
- Densidade: 160 dpi
- Orientação: landscape
- O código deve continuar compatível com telemóveis Android

## Requisitos da v0.1

- Escolher duas aplicações instaladas
- Mostrar o ícone e nome das aplicações
- Criar dois atalhos flutuantes agrupados
- Tocar num ícone abre directamente a aplicação
- Pressão prolongada permite mover o grupo
- Encostar o grupo à margem mais próxima
- Guardar as aplicações e posição
- Utilizar TYPE_APPLICATION_OVERLAY
- Utilizar um foreground service
- Reiniciar depois de BOOT_COMPLETED
- Manter o overlay após suspensão e retoma
- Mostrar uma notificação permanente discreta enquanto estiver activo

## Prioridades

1. Estabilidade
2. Funcionamento no ZQ9109
3. Baixo consumo de memória
4. Arranque automático
5. Interface simples e segura para utilização automóvel

## Regras de trabalho

- Fazer alterações pequenas e verificáveis
- Não alterar package name, minSdk, compileSdk ou targetSdk sem autorização
- Não utilizar APIs obsoletas quando exista uma alternativa estável
- Antes de concluir qualquer alteração, executar `.\gradlew.bat assembleDebug`
- Não considerar uma tarefa concluída se o build falhar
- Indicar todos os ficheiros alterados
- Não executar comandos destrutivos
- Não alterar configurações persistentes do Windows
