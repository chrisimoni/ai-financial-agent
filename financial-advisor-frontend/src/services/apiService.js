import axios from "axios";

const API_BASE_URL = "https://ai-financial-agent-vq2w.onrender.com";

class ApiService {
  constructor() {
    this.baseURL = API_BASE_URL;

    this.axios = axios.create({
      baseURL: API_BASE_URL,
      timeout: 30000,
      headers: {
        "Content-Type": "application/json",
      },
    });

    // Add request interceptor to include auth token
    this.axios.interceptors.request.use(
      (config) => {
        const token = this.getAuthToken();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    // Add response interceptor to handle errors
    this.axios.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          // Token expired or invalid
          this.setAuthToken(null);
          window.location.href = "/login";
        }
        return Promise.reject(error);
      }
    );

    console.log("ApiService initialized with baseURL:", this.baseURL);
  }

  setAuthToken(token) {
    this.authToken = token;
    if (token) {
      sessionStorage.setItem("authToken", token);
    } else {
      sessionStorage.removeItem("authToken");
    }
  }

  getAuthToken() {
    return this.authToken || sessionStorage.getItem("authToken");
  }

  // Auth endpoints - USE AXIOS instead of fetch
  async getCurrentUser() {
    try {
      const token = this.getAuthToken();

      if (!token) {
        throw new Error("No authentication token available");
      }

      const response = await this.axios.get("/auth/user");
      console.log("User data received:", response.data);
      return response.data;
    } catch (error) {
      console.error("getCurrentUser error:", error);

      if (error.response?.status === 401) {
        console.log("Token invalid, clearing...");
        this.setAuthToken(null);
      }

      throw error;
    }
  }

  async logout() {
    try {
      await this.axios.post("/api/auth/logout");
      this.setAuthToken(null);
      sessionStorage.removeItem("authToken");
    } catch (error) {
      console.error("Logout error:", error);
    }
  }

  // Chat endpoints
  async sendMessage(message, sessionId) {
    try {
      const response = await this.axios.post("/api/chat/message", {
        message,
        sessionId,
      });
      return response.data;
    } catch (error) {
      console.error("Send message error:", error);
      throw new Error("Failed to send message");
    }
  }

  async getChatSessions() {
    try {
      const response = await this.axios.get("/api/chat/sessions");
      return response.data;
    } catch (error) {
      console.error("Get chat sessions error:", error);
      return [];
    }
  }

  async getChatHistory(sessionId) {
    try {
      const response = await this.axios.get(`/api/chat/history/${sessionId}`);
      return response.data;
    } catch (error) {
      console.error("Get chat history error:", error);
      return [];
    }
  }

  async updateOngoingInstructions(instructions) {
    try {
      const response = await this.axios.post("/api/chat/instructions", {
        instructions,
      });
      return response.data;
    } catch (error) {
      console.error("Update instructions error:", error);
      throw new Error("Failed to update instructions");
    }
  }

  // Webhook test endpoints
  async testEmailWebhook(testData) {
    try {
      const response = await this.axios.post(
        "/api/webhooks/test/email",
        testData
      );
      return response.data;
    } catch (error) {
      console.error("Test email webhook error:", error);
      throw new Error("Failed to test email webhook");
    }
  }

  // HubSpot connection
  async getHubSpotAuthUrl() {
    try {
      const response = await this.axios.get("/api/auth/hubspot");
      return response.data;
    } catch (error) {
      console.error("Get HubSpot auth URL error:", error);
      throw new Error("Failed to get HubSpot auth URL");
    }
  }

  // Helper method for file uploads (if needed later)
  async uploadFile(file, endpoint) {
    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await this.axios.post(endpoint, formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });

      return response.data;
    } catch (error) {
      console.error("File upload error:", error);
      throw new Error("Failed to upload file");
    }
  }

  // Health check
  async healthCheck() {
    try {
      const response = await this.axios.get("/actuator/health");
      return response.data;
    } catch (error) {
      console.error("Health check error:", error);
      return { status: "DOWN" };
    }
  }
}

export const apiService = new ApiService();
