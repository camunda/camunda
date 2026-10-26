plugins { id("buildlogic.frontend-webjar-conventions") }

frontendWebjar {
  frontendBuildDirectory.set(layout.projectDirectory.dir("dist"))
  resourceTargetPath.set("META-INF/resources/admin")
}

description = "Identity Webjar"
