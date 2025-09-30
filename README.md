# AI Financial Advisor Agent

A comprehensive AI agent for Financial Advisors that integrates with Gmail, Google Calendar, and HubSpot CRM.

## Project Structure

```
financial-advisor-agent/
├── backend/                          # Spring Boot backend with Spring AI
│   ├── src/main/java/com/advisor/
│   │   ├── AdvisorAgentApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── OAuth2AuthenticationFailureHandler.java
│   │   │   ├── OAuth2AuthenticationSuccessHandler.java
│   │   │   └── OpenAIConfig.java
│   │   ├── controller/
│   │   │   ├── ChatController.java
│   │   │   ├── AuthController.java
│   │   │   └── WebhookController.java
│   │   ├── service/
│   │   │   ├── ChatService.java
│   │   │   ├── RAGService.java
│   │   │   ├── ToolService.java
│   │   │   ├── GmailService.java
│   │   │   ├── CalendarService.java
│   │   │   ├── HubSpotService.java
│   │   │   ├── TaskService.java
│   │   │   └── ProactiveAgentService.java
│   │   ├── model/
│   │   │   ├── User.java
│   │   │   ├── ChatMessage.java
│   │   │   ├── Task.java
│   │   │   ├── Contact.java
│   │   │   ├── Email.java
│   │   │   └── CalendarEvent.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── ChatMessageRepository.java
│   │   │   ├── TaskRepository.java
│   │   │   ├── ContactRepository.java
│   │   │   └── EmailRepository.java
│   │   ├── dto/
│   │   │   ├── ChatRequest.java
│   │   │   ├── ChatResponse.java
│   │   │   └── TaskRequest.java
│   │   └── tools/
│   │       ├── EmailTool.java
│   │       ├── CalendarTool.java
│   │       ├── HubSpotTool.java
│   │       └── SchedulingTool.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── schema.sql
│   │   
│   └── pom.xml
├── frontend/                         # React frontend
│   ├── public/
│   │   └── index.html
│   ├── src/
│   │   ├── components/
│   │   │   ├── Chat.js
│   │   │   ├── Login.js
│   │   │   ├── HubSpotCallback.js
│   │   │   ├── LoginSuccess.js
│   │   │   └── LoadingSpinner.js
│   │   ├── services/
│   │   │   ├── ApiService.js
│   │   │   └── websocket.js # To be implemented later
│   │   ├── styles/
│   │   │   ├── App.css
│   │   │   └── Chat.css
│   │   ├── App.js
│   │   └── index.js
│   ├── package.json
│   └── tailwind.config.js
├── docker-compose.yml                # For local development
├── Dockerfile.backend
├── Dockerfile.frontend
└── README.md
```

## Key Features

### 🔐 Authentication & Integration
- Google OAuth2 with Gmail and Calendar permissions
- HubSpot CRM OAuth integration
- Secure token management and refresh

### 🤖 AI Agent Capabilities
- **RAG System**: Vector-based search using pgvector for emails and CRM data
- **Tool Calling**: Dynamic function execution for complex tasks
- **Memory & Tasks**: Persistent task storage with continuation capabilities
- **Proactive Behavior**: Webhook-driven automatic responses

### 📧 Email & Calendar Management
- Gmail integration for reading and sending emails
- Google Calendar for scheduling and availability checking
- Automated appointment scheduling with multi-step workflows

### 📊 CRM Integration
- HubSpot contact management
- Automated contact creation and note-taking
- Client interaction tracking

### 💬 Chat Interface
- ChatGPT-like responsive design
- Real-time messaging with WebSocket support
- Context-aware conversations with memory

## Technology Stack

- **Backend**: Spring Boot 3.x, Spring AI, Spring Security, Spring Data JPA
- **Frontend**: React 18, TailwindCSS, Axios, WebSocket
- **Database**: PostgreSQL with pgvector extension
- **AI**: OpenAI GPT-4, Spring AI Framework
- **Deployment**: Render for backend app & Vercel for frontend app

## Quick Start

1. **Setup Environment Variables**
2. **Run Database**: `docker-compose up postgres`
3. **Start Backend**: `cd backend && ./mvnw spring-boot:run`
4. **Start Frontend**: `cd frontend && npm start`
5. **Access**: http://localhost:3000

## Environment Variables Required

```bash
# OpenAI
OPENAI_API_KEY=your_openai_api_key

# Google OAuth
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# HubSpot
HUBSPOT_CLIENT_ID=your_hubspot_client_id
HUBSPOT_CLIENT_SECRET=your_hubspot_client_secret
HUBSPOT_REDIRECT_URI=your_hubspot_redirect_url

# Database
DATABASE_URL=postgresql://localhost:5432/advisor_agent
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=password

# JWT
JWT_SECRET=your_jwt_secret_key
```

## Deployment

The application is configured for deployment on both Render and Vercel with proper configuration files included.

## Next Steps

Start both applications and enjoy the amazing features it has.
Note that the backend application may take few minutes to start as we're current using render free-tier plan