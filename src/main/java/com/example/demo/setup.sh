#!/bin/bash
: '
/*
* Mac Development Environment Setup Script
* Author: Kiran Sahoo
* Created: January 2025
*
* README:
* -------
* This script automates the setup of a development environment on macOS.
* It installs and configures:
* - Homebrew package manager
* - Java Development Kit (via SDKMAN)
* - Maven build tool
* - Node.js and NPM with XYZ-specific configurations
* - Development tools (git, curl, vim, etc.)
* - GUI applications (Rancher Desktop, IntelliJ IDEA Community Edition)
*
* Prerequisites:
* -------------
* 1. macOS operating system
* 2. Administrator access
* 3. Internet connection
* 4. XYZG VPN connection (for specific configurations)
*
* Usage:
* ------
* 1. Save this script as setup-mac.sh
* 2. Make it executable: chmod +x setup-mac.sh
* 3. Run it: ./setup-mac.sh
*
* Post-Installation:
* ----------------
* 1. Generate PAT from Azure DevOps
* 2. Base64 encode your PAT using the provided command
* 3. Update .npmrc with your encoded PAT
* 4. Verify az-cacert.pem configuration
* 5. Restart terminal
*
* Note:
* ----
* - The script checks for existing installations before proceeding
* - It can be run multiple times safely
* - All proxy and certificate configurations are XYZG-specific
*/
'
print_status() {
    echo "-------------------------------------------"
    echo "$1"
    echo "-------------------------------------------"
}

check_and_install_brew() {
    print_status "Checking Homebrew installation..."
    if ! command -v brew &> /dev/null; then
        print_status "Installing Homebrew..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

        if [[ $(uname -m) == 'arm64' ]]; then
            eval "$(/opt/homebrew/bin/brew shellenv)"
            echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zshrc
        else
            eval "$(/usr/local/bin/brew shellenv)"
            echo 'eval "$(/usr/local/bin/brew shellenv)"' >> ~/.zshrc
        fi
    else
        print_status "Homebrew is already installed"
    fi
}

check_and_install_base_tools() {
    print_status "Checking base development tools..."
    local tools=("bash" "ca-certificates" "curl" "git" "git-lfs" "jq" "vim" "node")
    for tool in "${tools[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            echo "Installing $tool..."
            brew install "$tool"
        else
            echo "$tool is already installed"
        fi
    done
}

get_latest_maven_version() {
    curl -s https://maven.apache.org/download.cgi | grep -oP 'Apache Maven \K[0-9]+\.[0-9]+\.[0-9]+' | head -1
}

get_latest_java_version() {
    sdk list java | grep -oP "21\..*-tem" | head -1 | tr -d ' '
}

setup_env_vars() {
    print_status "Checking environment variables..."
    if ! grep -q "HOMEBREW_FORCE_BREWED" ~/.zshrc; then
        print_status "Setting up environment variables in ~/.zshrc"
        cat << 'EOF' >> ~/.zshrc

# Homebrew preferences
export HOMEBREW_FORCE_BREWED_CA_CERTIFICATES=1
export HOMEBREW_FORCE_BREWED_GIT=1
export HOMEBREW_FORCE_BREWED_CURL=1

# Certificate settings
export CA_BUNDLE=$(brew --prefix)/etc/ca-certificates/cert.pem
export CURL_CA_BUNDLE=$CA_BUNDLE
export NODE_EXTRA_CA_CERTS=$CA_BUNDLE
export REQUESTS_CA_BUNDLE=$CA_BUNDLE

# Proxy settings
export HTTP_PROXY=http://127.0.0.1:9000
export HTTPS_PROXY=$HTTP_PROXY
export http_proxy=$HTTP_PROXY
export https_proxy=$HTTPS_PROXY

# PATH Setup
export JAVA_HOME=$HOME/.sdkman/candidates/java/current
export MAVEN_HOME=/opt/maven
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:/opt/homebrew/bin:$PATH

# NPM Certificate Settings
export NPM_CONFIG_CAFILE=/etc/az-cacert.pem
EOF
    else
        echo "Environment variables are already configured"
    fi
}

install_sdkman_java() {
    print_status "Checking SDKMAN and Java installation..."
    if ! command -v sdk &> /dev/null; then
        print_status "Installing SDKMAN..."
        curl -s "https://get.sdkman.io" | bash
        source "$HOME/.sdkman/bin/sdkman-init.sh"
    else
        echo "SDKMAN is already installed"
    fi

    if ! command -v java &> /dev/null; then
        print_status "Installing Java..."
        local JAVA_VERSION=$(get_latest_java_version)
        sdk install java ${JAVA_VERSION}
    else
        echo "Java is already installed"
        java -version
    fi
}

install_maven() {
    print_status "Checking Maven installation..."
    if ! command -v mvn &> /dev/null; then
        print_status "Installing Maven..."
        local MAVEN_VERSION=$(get_latest_maven_version)
        curl -O https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz
        tar xf apache-maven-${MAVEN_VERSION}-bin.tar.gz
        sudo mv apache-maven-${MAVEN_VERSION} /opt/maven

        if [ ! -f ~/.m2/settings.xml ]; then
            mkdir -p ~/.m2
            cat << 'EOF' > ~/.m2/settings.xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/POM/4.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
</settings>
EOF
        fi
    else
        echo "Maven is already installed"
        mvn -version
    fi
}

setup_npm() {
    print_status "Setting up NPM configuration..."

    # Check NPM installation
    if ! command -v npm &> /dev/null; then
        print_status "NPM not found. Installing Node.js and NPM..."
        brew install node
    else
        echo "NPM is already installed"
    fi

    # Check NPM proxy configuration
    if npm config get proxy | grep -q "zproxy.xyzg.net"; then
        echo "NPM proxy already configured"
    else
        print_status "Configuring NPM proxy settings..."
        npm config set proxy http://zproxy.xyzg.net:80
        npm config set https-proxy http://zproxy.xyzg.net:80
        npm config set strict-ssl false
    fi

    # Check for az-cacert.pem
    if [ -f "/etc/az-cacert.pem" ]; then
        echo "az-cacert.pem already exists"
    else
        print_status "Creating az-cacert.pem..."
        sudo mkdir -p /etc
        sudo touch /etc/az-cacert.pem
    fi

    # Check for .npmrc
    if [ -f ~/.npmrc ]; then
        echo ".npmrc already exists"
    else
        print_status "Creating .npmrc template..."
        cat << 'EOF' > ~/.npmrc
; begin auth token
//xyzg.pkgs.visualstudio.com/5xxxx/_packaging/IT.layman/npm/registry/:username=xyzg
//xyzg.pkgs.visualstudio.com/5xxxx/_packaging/IT.layman/npm/registry/:_password=[BASE64_ENCODED_PERSONAL_ACCESS_TOKEN]
//xyzg.pkgs.visualstudio.com/5xxxx/_packaging/IT.layman/npm/registry/:email=npm requires email to be set but doesn't use the value
//xyzg.pkgs.visualstudio.com/5xxxx/_packaging/IT.layman/npm/username=xyzg
//xyzg.pkgs.visualstudio.com/5xxxx/_packaging/IT.layman/npm/:_password=[BASE64_ENCODED_PERSONAL_ACCESS_TOKEN]
//xyzg.pkgs.visualstudio.com/5xxxx/_packaging/IT.layman/npm/:email=npm requires email to be set but doesn't use the value
; end auth token
EOF
    fi
}

install_apps() {
    print_status "Checking GUI applications..."
    local apps=("rancher" "intellij-idea-ce")
    for app in "${apps[@]}"; do
        if brew list --cask "$app" &> /dev/null; then
            echo "$app is already installed"
        else
            print_status "Installing $app..."
            brew install --cask "$app"
        fi
    done
}

main() {
    print_status "Starting Mac development environment setup..."

    check_and_install_brew
    export PATH="/opt/homebrew/bin:$PATH"
    check_and_install_base_tools
    setup_env_vars
    install_sdkman_java
    install_maven
    setup_npm
    install_apps
    source ~/.zshrc

    print_status "Verifying installations..."
    echo "Java version:"
    java -version
    echo "Maven version:"
    mvn -version
    echo "Brew version:"
    brew --version
    echo "Node version:"
    node -v
    echo "NPM version:"
    npm -v

    print_status "Setup complete! Please:"
    echo "1. Generate your Personal Access Token (PAT) from Azure DevOps"
    echo "2. Run the node command shown above to base64 encode your PAT"
    echo "3. Replace [BASE64_ENCODED_PERSONAL_ACCESS_TOKEN] in ~/.npmrc with your encoded PAT"
    echo "4. Make sure /etc/az-cacert.pem exists and has proper content"
    echo "5. Restart your terminal"
}

main