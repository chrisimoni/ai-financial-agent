import React, { useEffect, useState, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import "./Login.css";

const HubSpotCallback = ({ onLogin }) => {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState("processing");
  const navigate = useNavigate();
  const hasProcessed = useRef(false); // Prevent multiple processing

  useEffect(() => {
    // Prevent multiple executions
    if (hasProcessed.current) {
      return;
    }

    const code = searchParams.get("code");
    const error = searchParams.get("error");

    const handleHubSpotCallback = async (code) => {
      try {
        hasProcessed.current = true; // Mark as processed immediately
        setStatus("processing");

        const token = sessionStorage.getItem("authToken");

        if (!token) {
          throw new Error("No authentication token found");
        }

        // Send code to backend via authenticated API call
        const response = await fetch("/auth/hubspot/callback", {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ code }),
        });

        const data = await response.json();

        if (data.success) {
          setStatus("success");
          // Clear the code from URL to prevent reuse
          window.history.replaceState(
            {},
            document.title,
            window.location.pathname
          );

          setTimeout(() => {
            onLogin();
            navigate("/chat");
          }, 1500);
        } else {
          throw new Error(data.error || "HubSpot connection failed");
        }
      } catch (error) {
        console.error("HubSpot callback error:", error);
        setStatus("error");
        setTimeout(() => {
          onLogin();
          navigate("/chat");
        }, 2000);
      }
    };

    if (error) {
      hasProcessed.current = true;
      setStatus("error");
      setTimeout(() => {
        onLogin();
        navigate("/chat");
      }, 2000);
      return;
    }

    if (code && !hasProcessed.current) {
      handleHubSpotCallback(code);
    }
  }, []); // Empty dependency array - only run once

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1>Finalizing setup...</h1>
        </div>

        <div className="login-content">
          {status === "processing" && (
            <div className="status-section">
              <div className="loading-spinner"></div>
              <p>✅ Google account connected!</p>
              <p>🔄 Connecting HubSpot...</p>
            </div>
          )}

          {status === "success" && (
            <div className="status-section">
              <p>✅ Google account connected!</p>
              <p>✅ HubSpot connected successfully!</p>
              <p>🚀 Redirecting to your dashboard...</p>
            </div>
          )}

          {status === "error" && (
            <div className="status-section">
              <p>✅ Google account connected!</p>
              <p>⚠️ HubSpot connection failed, but you can connect it later.</p>
              <p>🚀 Redirecting to your dashboard...</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default HubSpotCallback;
