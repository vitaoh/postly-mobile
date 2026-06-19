# 📱 Postly

> Projeto acadêmico desenvolvido para a disciplina de **Dispositivos Móveis 2**  
> Curso de **Análise e Desenvolvimento de Sistemas — IFSP**

---

## 👥 Integrantes

| Nome | RA |
|------|----|
| Victor Herculini Rodrigues | AQ3028968 |
| Silvio Luiz Silva Santos | AQ3029492 |

---

## 📝 Descrição da Proposta

O **Postly** é uma rede social mobile focada no compartilhamento rápido de conteúdo e na interação entre usuários. A proposta central do aplicativo é oferecer uma experiência moderna, dinâmica e intuitiva, conectando pessoas por meio de publicações, curtidas, comentários e comunicação em tempo real.

O app foi desenvolvido nativamente para Android utilizando **Kotlin**, com integração completa ao ecossistema **Firebase** para autenticação, armazenamento e sincronização de dados em tempo real.

### Funcionalidades principais

- 🔐 **Autenticação** com login por nome de usuário e senha via Firebase Auth
- 📰 **Feed infinito** com as abas *For You* (Para Você) e *Seguindo*
- ❤️ **Curtidas e comentários** em publicações
- 👥 **Sistema de seguidores/seguindo** entre usuários
- 💬 **Chat em tempo real** entre usuários com suporte a mensagens de texto, imagem e voz
- 📸 **Criação de posts** com imagem capturada pela câmera
- 📍 **Publicações com localização** geográfica
- 👤 **Páginas de perfil** com informações do usuário e suas publicações

---

## 🔧 Recursos Utilizados

### 📡 Sensores

| Sensor | Uso no App |
|--------|-----------|
| **Acelerômetro** | Ao detectar que o dispositivo foi sacudido (*shake gesture*), o app abre automaticamente a câmera para capturar uma foto e criar uma nova publicação |
| **Câmera** | Captura de imagens para criação de posts e envio de fotos nas conversas do chat |
| **GPS / Localização** | Associação de coordenadas geográficas às publicações criadas pelos usuários |
| **Microfone** | Gravação e envio de mensagens de voz no chat em tempo real |

---

### 🔥 Firebase / Firestore

O Firestore é utilizado como banco de dados principal do aplicativo, com as seguintes coleções:

#### `users/{userId}`
Armazena o perfil de cada usuário (nome, username, e-mail, foto, etc.).

- **`followers/{followerId}`** — subcoleção com os seguidores do usuário
- **`following/{targetId}`** — subcoleção com os usuários que ele segue

#### `posts/{postId}`
Armazena as publicações com suporte a:
- Texto, imagem e localização
- Lista de curtidas (`likedBy`) e contador (`likeCount`)
- Contador de comentários (`commentCount`)
- **`comments/{commentId}`** — subcoleção com os comentários de cada post

#### `chats/{chatId}`
Gerencia as conversas entre dois usuários, com metadados da última mensagem, remetente e timestamp de atualização.

- **`messages/{messageId}`** — subcoleção com as mensagens individuais de cada conversa, suportando os tipos `text`, `image` e `audio`

> As regras de segurança do Firestore garantem que cada usuário só pode criar, editar e excluir seus próprios dados, e que apenas participantes de um chat podem ler suas mensagens.

---

## 🛠️ Tecnologias

- **Linguagem:** Kotlin
- **Plataforma:** Android
- **Backend:** Firebase Authentication + Cloud Firestore
- **Armazenamento de mídia:** Base64 inline no Firestore (dentro do limite de 1 MiB por documento)

---

## 🎥 Vídeo Demonstrativo

> 🔗 *Link do vídeo será adicionado em breve.*
