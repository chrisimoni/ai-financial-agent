import React, { useState } from "react";
import { GoogleLogo } from "./Icons";
import "./Login.css";

const Login = ({ onLogin }) => {
  const [isLoading, setIsLoading] = useState(false);

  const handleGoogleLogin = () => {
    setIsLoading(true);
    // Redirect to backend OAuth endpoint
    window.location.href = `${process.env.REACT_APP_API_URL}/oauth2/authorization/google`;
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1>Financial Advisor AI</h1>
          <p>
            Your intelligent assistant for managing client relationships,
            emails, and calendar
          </p>
        </div>

        <div className="login-content">
          <div className="login-section">
            <h2>Get Started</h2>
            <p>
              Connect your Google account to access Gmail and Calendar
              integration
            </p>

            <button
              className="oauth-button google-button"
              onClick={handleGoogleLogin}
              disabled={isLoading}
            >
              <GoogleLogo />
              <span>Continue with Google</span>
            </button>
          </div>

          <div className="features-section">
            <h3>What you can do:</h3>
            <ul className="features-list">
              <li>
                <span className="feature-icon">📧</span>
                <div>
                  <strong>Email Management</strong>
                  <p>
                    Ask questions about your emails and send responses
                    automatically
                  </p>
                </div>
              </li>
              <li>
                <span className="feature-icon">📅</span>
                <div>
                  <strong>Calendar Integration</strong>
                  <p>
                    Schedule appointments and manage your calendar with AI
                    assistance
                  </p>
                </div>
              </li>
              <li>
                <span className="feature-icon">👥</span>
                <div>
                  <strong>CRM Integration</strong>
                  <p>
                    Connect HubSpot to manage contacts and track client
                    interactions
                  </p>
                </div>
              </li>
              <li>
                <span className="feature-icon">🤖</span>
                <div>
                  <strong>Proactive Assistant</strong>
                  <p>
                    Set ongoing instructions for automatic responses and actions
                  </p>
                </div>
              </li>
            </ul>
          </div>

          <div className="example-queries">
            <h3>Example questions:</h3>
            <div className="query-examples">
              <div className="query-example">
                "Who mentioned their kid plays baseball?"
              </div>
              <div className="query-example">
                "Why did Greg say he wanted to sell AAPL stock?"
              </div>
              <div className="query-example">
                "Schedule an appointment with Sarah Smith"
              </div>
              <div className="query-example">
                "What meetings do I have this week?"
              </div>
            </div>
          </div>
        </div>

        <div className="login-footer">
          <p>
            By continuing, you agree to connect your Google account for Gmail
            and Calendar access.
          </p>
          <p className="privacy-note">
            Your data is processed securely and never shared with third parties.
          </p>
        </div>
      </div>

      {isLoading && (
        <div className="loading-overlay">
          <div className="loading-spinner"></div>
          <p>Connecting to Google...</p>
        </div>
      )}
    </div>
  );
};

export default Login;
