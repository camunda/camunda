# How to run a _Tasklist_ application locally using IntelliJ IDEA:

1. Build the project:
   - from the space of `tasklist/` folder run:

     ```shell
     ../mvnw clean install -DskipTests=true
     ```
2. Prepare your local env:
   - up `Zeebe` and `ElasticSearch` minimum required dependencies

     ```shell
     docker-compose up -d elasticsearch zeebe
     ```
3. Configure the Spring Boot run configuration:.
   - Go to "Run" > "Edit Configurations" in the top menu.
   - Click on the "+" button in the top left corner to add a new configuration.
   - Select "Spring Boot" from the list of templates.
   - In the "Name" field, give your run configuration a name, e.g., "TasklistSpringBootApp."
   - In the "Main class" field, click on the "Browse" button to select the main class of _tasklist_ Spring Boot application: `io.camunda.application.StandaloneTasklist` (located in `../dist/src/main/java/io/camunda/application/` folder).
   - In the "Active profiles" field, add the following profiles: `dev,dev-data,auth`.
   - Click "Apply" to save the configuration, and then click "OK" to close the dialog. As a result you will have:
     <img alt="SpringBoot app IntelliJ IDEA run configuration" src="/docs_assets/spring_boot_app_IDEA_config.png" width="550"/>
4. Run the application:
   - In the top right corner of the IntelliJ IDEA window, you'll see a dropdown with your new run configuration "TasklistSpringBootApp".
   - Click the green "Run" button (a &#9654; triangle icon) next to the dropdown to start the application.
   - IntelliJ IDEA will build the project and run the Spring Boot application. The application logs will be displayed in the "Run" tool window at the bottom of the IntelliJ IDEA window.
5. Access the application:
   - Once the application has started, you can access UI by hitting http://localhost:8080 or http://localhost:8080/swagger-ui/index.html URL to get REST API swagger page.

That's it! You have successfully run _Tasklist_ application locally :tada:.

## Troubleshooting :wrench:

In case if you faced with `java: cannot find symbol` errors after clicking on "Run" &#9654; button:
<img alt="cannot find symbol error illustration" src="/docs_assets/cannot_find_symbol_error.png" width="550"/>

Do the following steps:
- Click on "File" -> "Project Structure..."
- Select "SDKs" under "Platform Settings" section
- Select your JDK (17 or above)
- Click on "+" button and select `tasklist-mvc-auth-commons-x.x.x-SNAPSHOT.jar` jar file located under the `~/tasklist/mvc-auth-commons/target/` folder. So it should look like:
<img alt="fix for cannot find symbol error" src="/docs_assets/cannot_find_symbol_error_fix.png" width="550"/>

