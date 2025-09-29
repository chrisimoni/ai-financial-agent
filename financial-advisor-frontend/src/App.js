import React, { useState, useEffect } from "react";
import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
} from "react-router-dom";
import Login from "./components/Login";
import Chat from "./components/Chat";
import LoadingSpinner from "./components/LoadingSpinner";
import LoginSuccess from "./components/LoginSuccess";
import HubSpotCallback from "./components/HubSpotCallback";
import { apiService } from "./services/apiService";
import "./App.css";

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const token = sessionStorage.getItem("authToken");
      if (token) {
        apiService.setAuthToken(token);
        const userData = await apiService.getCurrentUser();
        setUser(userData);
      }
    } catch (error) {
      console.error("Auth check failed:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (token, userData) => {
    if (token && !userData) {
      // If only token is provided, fetch user data
      try {
        apiService.setAuthToken(token);
        const fetchedUserData = await apiService.getCurrentUser();
        setUser(fetchedUserData);
      } catch (error) {
        console.error("Failed to fetch user data:", error);
      }
    } else if (token && userData) {
      sessionStorage.setItem("authToken", token);
      apiService.setAuthToken(token);
      setUser(userData);
    } else {
      // Just refresh user data
      try {
        const refreshedUserData = await apiService.getCurrentUser();
        setUser(refreshedUserData);
      } catch (error) {
        console.error("Failed to refresh user data:", error);
      }
    }
  };

  const handleLogout = () => {
    sessionStorage.removeItem("authToken");
    apiService.setAuthToken(null);
    setUser(null);
  };

  if (loading) {
    return <LoadingSpinner />;
  }

  return (
    <Router>
      <div className="App">
        <Routes>
          <Route
            path="/login"
            element={
              user ? <Navigate to="/chat" /> : <Login onLogin={handleLogin} />
            }
          />
          <Route
            path="/login/success"
            element={<LoginSuccess onLogin={handleLogin} />}
          />
          <Route
            path="/auth/hubspot/callback"
            element={<HubSpotCallback onLogin={handleLogin} />}
          />
          <Route path="/login/error" element={<LoginError />} />
          <Route
            path="/chat"
            element={
              user ? (
                <Chat user={user} onLogout={handleLogout} />
              ) : (
                <Navigate to="/login" />
              )
            }
          />
          <Route
            path="/"
            element={<Navigate to={user ? "/chat" : "/login"} />}
          />
        </Routes>
      </div>
    </Router>
  );
}

function LoginError() {
  return (
    <div className="login-callback error">
      <h2>Login Failed</h2>
      <p>There was an error during the login process.</p>
      <button onClick={() => (window.location.href = "/login")}>
        Try Again
      </button>
    </div>
  );
}

export default App;
