#!/bin/bash
# Script to prepare Test Case Generator Agent for extraction to team-qa-engineering
# This creates a package with all necessary files for migration

set -e

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║   Test Case Generator Agent - Extraction Script              ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Configuration
EXTRACTION_DIR="/tmp/test-case-generator-agent-extract"
SOURCE_ROOT="/home/runner/work/camunda/camunda"

# Clean extraction directory
echo "🧹 Cleaning extraction directory..."
rm -rf "$EXTRACTION_DIR"
mkdir -p "$EXTRACTION_DIR"

# Create directory structure
echo "📁 Creating directory structure..."
mkdir -p "$EXTRACTION_DIR/src/main/java/com/camunda/qa/testgen"
mkdir -p "$EXTRACTION_DIR/src/main/resources"
mkdir -p "$EXTRACTION_DIR/src/test/java/com/camunda/qa/testgen"
mkdir -p "$EXTRACTION_DIR/docs"
mkdir -p "$EXTRACTION_DIR/scripts"
mkdir -p "$EXTRACTION_DIR/.github/workflows"

# Copy source files
echo "📦 Copying source files..."
cp "$SOURCE_ROOT/gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/"*.java \
   "$EXTRACTION_DIR/src/main/java/com/camunda/qa/testgen/" 2>/dev/null || true

# Copy test files
echo "🧪 Copying test files..."
cp "$SOURCE_ROOT/gateways/gateway-mcp/src/test/java/io/camunda/gateway/mcp/tool/testgen/"*.java \
   "$EXTRACTION_DIR/src/test/java/com/camunda/qa/testgen/" 2>/dev/null || true

# Copy documentation
echo "📖 Copying documentation..."
cp "$SOURCE_ROOT/gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/"*.md \
   "$EXTRACTION_DIR/docs/" 2>/dev/null || true

# Copy Postman collection
cp "$SOURCE_ROOT/gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/"*.json \
   "$EXTRACTION_DIR/docs/" 2>/dev/null || true

# Copy configuration example
echo "⚙️  Copying configuration..."
cp "$SOURCE_ROOT/gateways/gateway-mcp/src/main/resources/application-testgen-example.yaml" \
   "$EXTRACTION_DIR/src/main/resources/application.yaml" 2>/dev/null || true

# Copy scripts
echo "📜 Copying scripts..."
cp "$SOURCE_ROOT/scripts/"*testgen*.sh "$EXTRACTION_DIR/scripts/" 2>/dev/null || true

# Update package names in Java files
echo "🔄 Updating package names..."
find "$EXTRACTION_DIR/src" -name "*.java" -type f -exec sed -i \
  's/io\.camunda\.gateway\.mcp\.tool\.testgen/com.camunda.qa.testgen/g' {} \;

# Update import statements
find "$EXTRACTION_DIR/src" -name "*.java" -type f -exec sed -i \
  's/import io\.camunda\.gateway\.mcp\.mapper\.CallToolResultMapper/import com.camunda.qa.testgen.mapper.CallToolResultMapper/g' {} \;

# Update MCP endpoint in scripts
echo "🔧 Updating scripts for new endpoint..."
sed -i 's|http://localhost:8080/mcp|http://localhost:8081/mcp|g' "$EXTRACTION_DIR/scripts/"*.sh 2>/dev/null || true

# Create pom.xml
echo "📝 Creating pom.xml..."
cat > "$EXTRACTION_DIR/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.camunda.qa</groupId>
    <artifactId>test-case-generator-agent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Test Case Generator Agent</name>
    <description>MCP agent for generating test cases from GitHub Epics to TestRail</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring AI MCP Server -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.springaicommunity</groupId>
            <artifactId>mcp-annotations</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-core</artifactId>
            <version>1.0.0</version>
        </dependency>

        <!-- Jackson for JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Test dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

# Create Application.java
echo "🚀 Creating main application class..."
cat > "$EXTRACTION_DIR/src/main/java/com/camunda/qa/testgen/TestCaseGeneratorApplication.java" << 'EOF'
package com.camunda.qa.testgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestCaseGeneratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestCaseGeneratorApplication.class, args);
    }
}
EOF

# Create Dockerfile
echo "🐳 Creating Dockerfile..."
cat > "$EXTRACTION_DIR/Dockerfile" << 'EOF'
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="camunda-qa-team"
WORKDIR /app
COPY target/test-case-generator-agent-*.jar app.jar
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# Create docker-compose.yaml
echo "🐋 Creating docker-compose.yaml..."
cat > "$EXTRACTION_DIR/docker-compose.yaml" << 'EOF'
version: '3.8'

services:
  test-case-generator:
    build: .
    container_name: test-case-generator-agent
    ports:
      - "8081:8081"
    environment:
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - TESTRAIL_URL=${TESTRAIL_URL}
      - TESTRAIL_USERNAME=${TESTRAIL_USERNAME}
      - TESTRAIL_API_KEY=${TESTRAIL_API_KEY}
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    restart: unless-stopped
EOF

# Create .gitignore
echo "🙈 Creating .gitignore..."
cat > "$EXTRACTION_DIR/.gitignore" << 'EOF'
# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties

# IDE
.idea/
*.iml
.vscode/
.settings/
.classpath
.project

# Environment
.env
.env.testgen
*.log

# OS
.DS_Store
Thumbs.db
EOF

# Create README.md
echo "📄 Creating README.md..."
cat > "$EXTRACTION_DIR/README.md" << 'EOF'
# Test Case Generator Agent

Standalone MCP server for generating test cases from GitHub Epics to TestRail.

## Quick Start

1. Configure credentials:
   ```bash
   ./scripts/setup-testgen-agent.sh
   ```

2. Build and run:
   ```bash
   ./mvnw clean package
   java -jar target/test-case-generator-agent-*.jar
   ```

3. Or use Docker:
   ```bash
   docker-compose up
   ```

## Documentation

- [Usage Guide](docs/USAGE_GUIDE.md)
- [Migration Guide](docs/MIGRATION_GUIDE.md)
- [Implementation Summary](docs/IMPLEMENTATION_SUMMARY.md)

## MCP Endpoint

http://localhost:8081/mcp

## Testing

```bash
./scripts/test-testgen-agent.sh all
```
EOF

# Create GitHub Actions workflow
echo "⚙️  Creating CI/CD workflow..."
cat > "$EXTRACTION_DIR/.github/workflows/build.yaml" << 'EOF'
name: Build and Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven
    
    - name: Build with Maven
      run: ./mvnw clean package -DskipTests
    
    - name: Run tests
      run: ./mvnw test
    
    - name: Build Docker image
      run: docker build -t camunda/test-case-generator-agent:${{ github.sha }} .
    
    - name: Save Docker image
      if: github.ref == 'refs/heads/main'
      run: docker save camunda/test-case-generator-agent:${{ github.sha }} | gzip > image.tar.gz
    
    - name: Upload artifact
      if: github.ref == 'refs/heads/main'
      uses: actions/upload-artifact@v3
      with:
        name: docker-image
        path: image.tar.gz
EOF

# Create archive
echo "📦 Creating archive..."
cd "$EXTRACTION_DIR"
tar -czf ../test-case-generator-agent.tar.gz .
cd - > /dev/null

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║   Extraction Complete!                                        ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "📦 Extracted to: $EXTRACTION_DIR"
echo "📦 Archive created: /tmp/test-case-generator-agent.tar.gz"
echo ""
echo "📋 Files extracted:"
ls -lh "$EXTRACTION_DIR" | tail -n +2
echo ""
echo "🎯 Next Steps:"
echo ""
echo "1️⃣  Review extracted files:"
echo "   cd $EXTRACTION_DIR"
echo ""
echo "2️⃣  Copy to team-qa-engineering repository:"
echo "   cd /path/to/team-qa-engineering"
echo "   tar -xzf /tmp/test-case-generator-agent.tar.gz"
echo ""
echo "3️⃣  Build and test:"
echo "   ./mvnw clean package"
echo "   java -jar target/test-case-generator-agent-*.jar"
echo ""
echo "4️⃣  Or use Docker:"
echo "   docker-compose up"
echo ""
echo "📖 See MIGRATION_GUIDE.md in docs/ for detailed instructions"
echo ""
echo "✅ Ready for migration to team-qa-engineering!"
