# Comida de Vó — Financeiro

Aplicativo Android nativo para controle financeiro da Comida de Vó.

## O que o app faz

- Funciona offline com banco local SQLite.
- Permite lançar entradas e gastos, editar e excluir lançamentos.
- Calcula entradas, gastos e saldo.
- Sincroniza os lançamentos com uma conta Google usando a API Google Drive.
- O backup fica visível no Google Drive na pasta **Comida de Vó - Financeiro**.
- Usa IDs e `updatedAt` para mesclar alterações feitas offline e na nuvem.
- Faz sincronização automática periódica quando há internet e uma conta Google conectada.
- Também oferece sincronização manual pelo botão **Sincronizar agora**.
- Projeto preparado para gerar APK via GitHub Actions.

## Configuração obrigatória do Google

O aplicativo precisa de um projeto no Google Cloud para o OAuth do Android.

1. Crie/seleciona um projeto no Google Cloud Console.
2. Ative a **Google Drive API**.
3. Configure o **Google Auth Platform / Branding** com o nome `Comida de Vó — Financeiro` e um e-mail de suporte.
4. Crie um cliente OAuth 2.0 do tipo **Android**.
5. Informe o pacote Android: `com.comidadevo.financeiro`.
6. Informe o SHA-1 do certificado usado para assinar o APK que será instalado.
7. O aplicativo solicita somente o escopo `drive.file`, que é usado para criar e atualizar o arquivo de backup criado pelo próprio aplicativo.

### Observação importante sobre o APK

Para que o login Google funcione de forma estável no APK distribuído pelo GitHub Actions, o ideal é assinar o APK de produção com um keystore próprio e cadastrar o SHA-1 desse certificado no cliente OAuth Android. A chave privada do keystore nunca deve ser colocada no repositório.

## Fluxo esperado no celular

1. Instalar o APK.
2. Abrir **Comida de Vó — Financeiro**.
3. Tocar em **Conectar Google**.
4. Escolher a conta Google e conceder acesso ao Drive.
5. O app cria automaticamente a pasta `Comida de Vó - Financeiro` no Meu Drive e grava o backup nela.
6. A partir daí, cada novo lançamento é salvo localmente primeiro e sincronizado com o Google Drive quando houver conexão.
7. Em outro aparelho, conectar a mesma conta Google faz o app recuperar os lançamentos existentes do backup.

## Segurança

O app não usa `appDataFolder` para o backup principal. O backup fica em uma pasta normal do Meu Drive para não depender de uma pasta oculta que pode ser removida junto com a desinstalação do aplicativo. O acesso é limitado ao escopo `drive.file`.
