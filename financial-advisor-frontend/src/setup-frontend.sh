#!/bin/bash
# setup-frontend.sh - Frontend setup script

echo "🌐 Setting up Financial Advisor AI Frontend..."

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js not found. Please install Node.js 18 or higher."
    exit 1
fi

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    echo "❌ npm not found. Please install npm."
    exit 1
fi

echo "✅ Node.js and npm found"

# Install dependencies
echo "📦 Installing dependencies..."
npm install

# Check if installation was successful
if [ $? -eq 0 ]; then
    echo "✅ Dependencies installed successfully"
else
    echo "❌ Failed to install dependencies"
    exit 1
fi

# Create .env.local for local development
if [ ! -f .env.local ]; then
    echo "📝 Creating .env.local for development..."
    cat > .env.local << EOF
# Frontend environment variables
REACT_APP_API_URL=http://localhost:8080
GENERATE_SOURCEMAP=false
EOF
    echo "✅ Created .env.local"
fi

# Create a simple favicon.ico placeholder if it doesn't exist
if [ ! -f public/favicon.ico ]; then
    echo "🎨 Creating placeholder favicon..."
    # Create a simple 16x16 ico file (base64 encoded)
    echo "Creating favicon placeholder..."
    touch public/favicon.ico
    echo "✅ Created favicon placeholder"
fi

# Create startup script
cat > start-dev.sh << 'EOF'
#!/bin/bash
echo "🌐 Starting Financial Advisor AI Frontend..."
echo "Frontend will be available at: http://localhost:3000"
echo "Make sure your backend is running on http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop"
npm start
EOF
chmod +x start-dev.sh

# Create build script
cat > build-frontend.sh << 'EOF'
#!/bin/bash
echo "🏗️  Building Financial Advisor AI Frontend for production..."

# Set production environment variables
export REACT_APP_API_URL=${REACT_APP_API_URL:-https://your-backend.herokuapp.com}
export GENERATE_SOURCEMAP=false

# Build the project
npm run build

if [ $? -eq 0 ]; then
    echo "✅ Frontend built successfully!"
    echo "📁 Build files are in the 'build' directory"
    echo ""
    echo "To serve locally:"
    echo "  npx serve -s build -l 3000"
    echo ""
    echo "To deploy to Render:"
    echo "  1. Connect your GitHub repo to Render"
    echo "  2. Set build command: npm run build"
    echo "  3. Set start command: npx serve -s build -l 3000"
    echo "  4. Set environment variable: REACT_APP_API_URL=https://your-backend.herokuapp.com"
else
    echo "❌ Build failed"
    exit 1
fi
EOF
chmod +x build-frontend.sh

echo ""
echo "✅ Frontend setup complete!"
echo ""
echo "📋 Next steps:"
echo "1. Start the development server:"
echo "   ./start-dev.sh"
echo "   OR"
echo "   npm start"
echo ""
echo "2. Open your browser to: http://localhost:3000"
echo ""
echo "3. Make sure your backend is running on: http://localhost:8080"
echo ""
echo "🚀 For production build:"
echo "   ./build-frontend.sh"