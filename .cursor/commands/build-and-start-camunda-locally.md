# build-and-start-camunda-locally

Build and Start Camunda Locally
Step 1: Clean Previous Data
Remove data from previous runs by deleting the ../../data/ directory relative to the camunda workspace.

Step 2: Build Camunda
Execute Maven build with the following flags:

clean install - Clean and build the project
-DskipTests - Skip test execution
-Dskip.fe.build=false - Build frontend components
-T1C - Use 1 thread per CPU core for parallel builds
-DskipChecks - Skip quality checks
Command: mvn clean install -DskipTests -Dskip.fe.build=false -T1C -DskipChecks

Step 3: Start Elasticsearch
Navigate to operate/ directory and start Elasticsearch using Docker Compose:

Start the service with docker-compose up -d elasticsearch
Verify it's running with docker-compose ps
Step 4: Run Camunda
Navigate to dist/target/camunda-zeebe/ and execute the Camunda binary:

Command: ./bin/camunda
The application will run in the foreground until interrupted.

Step 5: Cleanup
After stopping Camunda (Ctrl+C):

Navigate to operate/ directory
Tear down Elasticsearch with volumes: docker-compose down elasticsearch -v
Key Files
Main build file: /Users/aleksander.dytko/Documents/GitHub/camunda/pom.xml
Docker Compose: /Users/aleksander.dytko/Documents/GitHub/camunda/operate/docker-compose.yml
Built executable: dist/target/camunda-zeebe/bin/camunda

This command will be available in chat with /build-and-start-camunda-locally
