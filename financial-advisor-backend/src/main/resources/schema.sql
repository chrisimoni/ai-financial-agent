CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     email VARCHAR(255) UNIQUE NOT NULL,
                                     name VARCHAR(255),
                                     google_access_token TEXT,
                                     google_refresh_token TEXT,
                                     hubspot_access_token TEXT,
                                     hubspot_refresh_token TEXT,
                                     ongoing_instructions TEXT,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
                                             id BIGSERIAL PRIMARY KEY,
                                             user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
                                             content TEXT NOT NULL,
                                             role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
                                             session_id VARCHAR(255),
                                             timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                             metadata TEXT
);

CREATE TABLE IF NOT EXISTS tasks (
                                     id BIGSERIAL PRIMARY KEY,
                                     user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
                                     title VARCHAR(500),
                                     description TEXT,
                                     status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                                         CHECK (status IN ('PENDING', 'IN_PROGRESS', 'WAITING_FOR_RESPONSE', 'COMPLETED', 'FAILED', 'CANCELLED')),
                                     type VARCHAR(50) NOT NULL
                                         CHECK (type IN ('SCHEDULE_APPOINTMENT', 'SEND_EMAIL', 'CREATE_CONTACT', 'UPDATE_CONTACT', 'CALENDAR_EVENT', 'FOLLOW_UP', 'RESEARCH', 'PROACTIVE_ACTION')),
                                     context TEXT,
                                     result TEXT,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     scheduled_at TIMESTAMP,
                                     completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS contacts (
                                        id BIGSERIAL PRIMARY KEY,
                                        user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
                                        hubspot_id VARCHAR(255),
                                        name VARCHAR(255),
                                        email VARCHAR(255),
                                        company VARCHAR(255),
                                        phone VARCHAR(50),
                                        notes TEXT,
                                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                        last_sync_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS emails (
                                      id BIGSERIAL PRIMARY KEY,
                                      user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
                                      gmail_id VARCHAR(255) UNIQUE,
                                      from_email VARCHAR(255),
                                      from_name VARCHAR(255),
                                      to_email VARCHAR(255),
                                      subject TEXT,
                                      body TEXT,
                                      sent_at TIMESTAMP,
                                      received_at TIMESTAMP,
                                      indexed_at TIMESTAMP,
                                      is_read BOOLEAN DEFAULT FALSE,
                                      is_important BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS vector_store (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            content TEXT NOT NULL,
                                            metadata JSON,
                                            embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_user_session ON chat_messages(user_id, session_id);
CREATE INDEX IF NOT EXISTS idx_tasks_user_status ON tasks(user_id, status);
CREATE INDEX IF NOT EXISTS idx_contacts_user_email ON contacts(user_id, email);
CREATE INDEX IF NOT EXISTS idx_emails_user_received ON emails(user_id, received_at);
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx ON vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
