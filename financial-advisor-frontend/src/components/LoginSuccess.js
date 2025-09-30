import React, { useEffect, useState, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import "./Login.css";

const LoginSuccess = ({ onLogin }) => {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState("processing");
  const navigate = useNavigate();
  const hasProcessed = useRef(false); // Prevent multiple processing

  useEffect(() => {
    // Prevent multiple executionsF
    if (hasProcessed.current) {
      return;
    }

    const token = searchParams.get("token");

    const connectHubSpot = async () => {
      try {
        hasProcessed.current = true; // Mark as processed immediately
        setStatus("connecting-hubspot");

        // Store token first
        if (token) {
          sessionStorage.setItem("authToken", token);
        }

        const authToken = sessionStorage.getItem("authToken");
        if (!authToken) {
          throw new Error("No authentication token available");
        }

        const response = await fetch("http://localhost:8085/auth/hubspot", {
          headers: {
            Authorization: `Bearer ${authToken}`,
          },
        });

        const data = await response.json();

        if (data.authUrl) {
          // Clear the token from URL before redirecting
          window.history.replaceState(
            {},
            document.title,
            window.location.pathname
          );
          window.location.href = data.authUrl;
        } else {
          throw new Error("No HubSpot auth URL received");
        }
      } catch (error) {
        console.error("Error connecting to HubSpot:", error);
        setStatus("hubspot-error");
        setTimeout(() => {
          onLogin();
          navigate("/chat");
        }, 2000);
      }
    };

    if (!token) {
      navigate("/login/error");
      return;
    }

    if (!hasProcessed.current) {
      connectHubSpot();
    }
  }, []); // Empty dependency array - only run once

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1>Setting up your account...</h1>
        </div>

        <div className="login-content">
          {status === "processing" && (
            <div className="status-section">
              <div className="loading-spinner"></div>
              <p>✅ Google account connected successfully!</p>
              <p>🔄 Processing authentication...</p>
            </div>
          )}

          {status === "connecting-hubspot" && (
            <div className="status-section">
              <div className="loading-spinner"></div>
              <p>✅ Google account connected successfully!</p>
              <p>🔄 Connecting to HubSpot...</p>
              <p className="note">
                You'll be redirected to HubSpot to authorize access.
              </p>
            </div>
          )}

          {status === "hubspot-error" && (
            <div className="status-section">
              <p>✅ Google account connected successfully!</p>
              <p>
                ⚠️ HubSpot connection failed, but you can proceed without it.
              </p>
              <p>Redirecting to your dashboard...</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default LoginSuccess;
