<#--
  This template takes inspirtion from the examples provided by the mojoHaus license-maven-plugin repository
  https://github.com/mojohaus/license-maven-plugin/blob/master/src/main/resources/org/codehaus/mojo/license/third-party-file.ftl
-->
<#-- To render the third-party file.
 Available context :
 - dependencyMap a collection of Map.Entry with
   key are dependencies (as a MavenProject) (from the maven project)
   values are licenses of each dependency (array of string)
 - licenseMap a collection of Map.Entry with
   key are licenses of each dependency (array of string)
   values are all dependencies using this license
-->
<#function licenseFormat licenses>
    <#assign result = ""/>
    <#list licenses as license>
        <#assign result = result + "(" +license + ") "/>
    </#list>
    <#return result>
</#function>
<#function artifactFormat project>
    <#if project.url??>
        <#return "* [" + project.groupId + ":" + project.artifactId + ":" + project.version + "](" + project.url + ")">
    <#else>
        <#return "* " + project.groupId + ":" + project.artifactId + ":" + project.version>
    </#if>
</#function>

<#list dependencyMap as e>
    <#assign project = e.getKey()/>
    <#assign licenses = e.getValue()/>
    ${artifactFormat(project)} ${licenseFormat(licenses)}
</#list>
