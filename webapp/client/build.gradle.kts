plugins { id("buildlogic.frontend-webjar-conventions") }

frontendWebjar {
  frontendBuildDirectory.set(layout.projectDirectory.dir("apps/orchestration-cluster-webapp/dist"))
  resourceTargetPath.set("META-INF/resources/webapp")
}

description = "Webapp Webjar"
