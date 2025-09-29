import React, { useState, useEffect, useRef } from "react";
import {
  Send,
  Plus,
  X,
  Settings,
  User,
  Calendar,
  Users,
  MessageSquare,
  History,
  LogOut,
  Clock,
} from "lucide-react";
import { apiService } from "../services/apiService";
import { v4 as uuidv4 } from "uuid";
import "./Chat.css";

const Chat = ({ user, onLogout }) => {
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState(null);
  const [activeTab, setActiveTab] = useState("chat");
  const [chatHistory, setChatHistory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [ongoingInstructions, setOngoingInstructions] = useState("");
  const [showInstructions, setShowInstructions] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    loadOngoingInstructions();
  }, []);

  useEffect(() => {
    if (sessionId) {
      loadChatHistory();
    }
  }, [sessionId]);

  // Load chat sessions when history tab is clicked
  useEffect(() => {
    if (activeTab === "history") {
      loadChatSessions();
    }
  }, [activeTab]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  const loadChatHistory = async () => {
    try {
      const history = await apiService.getChatHistory(sessionId);
      if (history && history.length > 0) {
        setMessages(history);
      }
    } catch (error) {
      console.error("Failed to load chat history:", error);
    }
  };

  const loadChatSessions = async () => {
    try {
      setLoadingHistory(true);
      const sessions = await apiService.getChatSessions();
      setChatHistory(sessions);
    } catch (error) {
      console.error("Failed to load chat sessions:", error);
    } finally {
      setLoadingHistory(false);
    }
  };

  const loadOngoingInstructions = async () => {
    try {
      const userData = await apiService.getCurrentUser();
      if (userData.ongoingInstructions) {
        setOngoingInstructions(userData.ongoingInstructions);
      }
    } catch (error) {
      console.error("Failed to load ongoing instructions:", error);
    }
  };

  const handleSendMessage = async () => {
    if (!inputValue.trim() || isLoading) return;

    const userMessage = {
      id: Date.now(),
      content: inputValue,
      role: "USER",
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputValue("");
    setIsLoading(true);

    try {
      const currentSessionId = sessionId || uuidv4();
      if (!sessionId) {
        console.log("Creating new session:", currentSessionId);
        setSessionId(currentSessionId);
      }

      const response = await apiService.sendMessage(
        inputValue,
        currentSessionId
      );

      const assistantMessage = {
        id: Date.now() + 1,
        content: response.message,
        role: "ASSISTANT",
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (error) {
      console.error("Failed to send message:", error);
      const errorMessage = {
        id: Date.now() + 1,
        content: "Sorry, I encountered an error. Please try again.",
        role: "ASSISTANT",
        timestamp: new Date(),
        isError: true,
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const startNewThread = () => {
    setMessages([]);
    setSessionId(null);
    setActiveTab("chat");
  };

  const loadSession = async (session) => {
    try {
      setSessionId(session.sessionId);
      const history = await apiService.getChatHistory(session.sessionId);
      setMessages(history);
      setActiveTab("chat");
    } catch (error) {
      console.error("Failed to load session:", error);
    }
  };

  const handleUpdateInstructions = async () => {
    try {
      await apiService.updateOngoingInstructions(ongoingInstructions);
      setShowInstructions(false);
      const confirmMessage = {
        id: Date.now(),
        content: "Ongoing instructions updated successfully!",
        role: "ASSISTANT",
        timestamp: new Date(),
        isSystem: true,
      };
      setMessages((prev) => [...prev, confirmMessage]);
    } catch (error) {
      console.error("Failed to update instructions:", error);
    }
  };

  const formatTimestamp = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const formatDate = (timestamp) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffInDays = Math.floor((now - date) / (1000 * 60 * 60 * 24));

    if (diffInDays === 0) return "Today";
    if (diffInDays === 1) return "Yesterday";
    if (diffInDays < 7) return `${diffInDays} days ago`;

    return date.toLocaleDateString();
  };

  return (
    <div className="chat-container">
      <div className="chat-sidebar">
        <div className="chat-header">
          <h1>Ask Anything</h1>
        </div>

        <div className="chat-tabs">
          <button
            className={`tab ${activeTab === "chat" ? "active" : ""}`}
            onClick={() => setActiveTab("chat")}
          >
            <MessageSquare size={16} />
            Chat
          </button>
          <button
            className={`tab ${activeTab === "history" ? "active" : ""}`}
            onClick={() => setActiveTab("history")}
          >
            <History size={16} />
            History
          </button>
          <button className="new-thread-button" onClick={startNewThread}>
            <Plus size={16} />
            New thread
          </button>
        </div>

        <div className="user-section">
          <div className="user-info">
            <User size={20} />
            <span>{user.name}</span>
          </div>
          <div className="connection-status">
            <div
              className={`status-indicator ${
                user.hasGoogleAuth ? "connected" : "disconnected"
              }`}
            >
              Gmail: {user.hasGoogleAuth ? "Connected" : "Not Connected"}
            </div>
            <div
              className={`status-indicator ${
                user.hasHubSpotAuth ? "connected" : "disconnected"
              }`}
            >
              HubSpot: {user.hasHubSpotAuth ? "Connected" : "Not Connected"}
            </div>
          </div>
          <button
            className="instructions-button"
            onClick={() => setShowInstructions(true)}
          >
            <Settings size={16} />
            Ongoing Instructions
          </button>

          <button className="logout-button" onClick={onLogout}>
            <LogOut size={16} />
            Logout
          </button>
        </div>
      </div>

      <div className="chat-main">
        {activeTab === "chat" && (
          <>
            <div className="chat-messages">
              {messages.length === 0 ? (
                <div className="welcome-message">
                  <div className="welcome-content">
                    <h2>Welcome to your AI Financial Assistant!</h2>
                    <p>I can help you with:</p>
                    <div className="capability-grid">
                      <div className="capability-item">
                        <Calendar size={24} />
                        <span>
                          Schedule appointments and manage your calendar
                        </span>
                      </div>
                      <div className="capability-item">
                        <Users size={24} />
                        <span>
                          Answer questions about your clients and contacts
                        </span>
                      </div>
                      <div className="capability-item">
                        <MessageSquare size={24} />
                        <span>Send emails and manage communications</span>
                      </div>
                    </div>
                    <div className="example-queries">
                      <p>Try asking:</p>
                      <div className="query-suggestions">
                        <button
                          className="suggestion-button"
                          onClick={() =>
                            setInputValue(
                              "Who mentioned their kid plays baseball?"
                            )
                          }
                        >
                          "Who mentioned their kid plays baseball?"
                        </button>
                        <button
                          className="suggestion-button"
                          onClick={() =>
                            setInputValue(
                              "Schedule an appointment with Sarah Smith"
                            )
                          }
                        >
                          "Schedule an appointment with Sarah Smith"
                        </button>
                        <button
                          className="suggestion-button"
                          onClick={() =>
                            setInputValue("What meetings do I have this week?")
                          }
                        >
                          "What meetings do I have this week?"
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              ) : (
                messages.map((message) => (
                  <div
                    key={message.id}
                    className={`message ${(
                      message.role || "user"
                    ).toLowerCase()} ${message.isError ? "error" : ""} ${
                      message.isSystem ? "system" : ""
                    }`}
                  >
                    <div className="message-content">
                      <div className="message-text">{message.content}</div>
                      <div className="message-timestamp">
                        {formatTimestamp(message.timestamp)}
                      </div>
                    </div>
                  </div>
                ))
              )}
              {isLoading && (
                <div className="message assistant loading">
                  <div className="message-content">
                    <div className="typing-indicator">
                      <span></span>
                      <span></span>
                      <span></span>
                    </div>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            <div className="chat-input-container">
              <div className="chat-input">
                <textarea
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder="Ask anything about your meetings..."
                  rows={1}
                  disabled={isLoading}
                />
                <button
                  onClick={handleSendMessage}
                  disabled={!inputValue.trim() || isLoading}
                  className="send-button"
                >
                  <Send size={20} />
                </button>
              </div>
              <div className="input-footer">
                <div className="context-indicator">All meetings</div>
              </div>
            </div>
          </>
        )}

        {activeTab === "history" && (
          <div className="history-section">
            <h2>Chat History</h2>
            {loadingHistory ? (
              <div className="loading-spinner-container">
                <div className="loading-spinner"></div>
                <p>Loading chat history...</p>
              </div>
            ) : chatHistory.length === 0 ? (
              <div className="empty-state">
                <MessageSquare size={48} color="#9ca3af" />
                <p>No previous conversations yet</p>
                <p className="empty-state-subtitle">
                  Start a new chat to begin
                </p>
              </div>
            ) : (
              <div className="history-list">
                {chatHistory.map((session) => (
                  <div
                    key={session.sessionId}
                    className="history-item"
                    onClick={() => loadSession(session)}
                  >
                    <div className="history-item-header">
                      <MessageSquare size={16} />
                      <span className="history-item-title">
                        {session.preview || "Untitled conversation"}
                      </span>
                    </div>
                    <div className="history-item-meta">
                      <Clock size={14} />
                      <span>{formatDate(session.lastMessageAt)}</span>
                      <span className="history-item-count">
                        {session.messageCount}{" "}
                        {session.messageCount === 1 ? "message" : "messages"}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {showInstructions && (
        <div className="modal-overlay">
          <div className="modal">
            <div className="modal-header">
              <h3>Ongoing Instructions</h3>
              <button onClick={() => setShowInstructions(false)}>
                <X size={20} />
              </button>
            </div>
            <div className="modal-content">
              <p>
                Set instructions that I should remember and follow
                automatically:
              </p>
              <textarea
                value={ongoingInstructions}
                onChange={(e) => setOngoingInstructions(e.target.value)}
                placeholder="e.g., When someone emails me that is not in Hubspot, please create a contact in Hubspot with a note about the email."
                rows={6}
              />
              <div className="modal-actions">
                <button
                  onClick={() => setShowInstructions(false)}
                  className="cancel-button"
                >
                  Cancel
                </button>
                <button
                  onClick={handleUpdateInstructions}
                  className="save-button"
                >
                  Save Instructions
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Chat;
