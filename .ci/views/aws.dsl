import javaposse.jobdsl.dsl.views.jobfilter.MatchType
import javaposse.jobdsl.dsl.views.jobfilter.Status

listView('AWS') {

  jobFilters {
    regex {
      matchType(MatchType.INCLUDE_MATCHED)
      matchValue(RegexMatchValue.DESCRIPTION)
      regex('AWS-.*')
    }
  }

  recurse(true)

  configure { view ->

    // columns
    view / columns << {
      'hudson.views.StatusColumn' {}
      'hudson.views.WeatherColumn' {}
      'hudson.views.BuildButtonColumn' {}
      'jenkins.plugins.extracolumns.LastBuildConsoleColumn' {}
      'jenkins.plugins.extracolumns.ConfigureProjectColumn' {}
      'hudson.views.JobColumn' {}
      'com.robestone.hudson.compactcolumns.LastSuccessAndFailedColumn' {
        timeAgoTypeString 'DIFF'
      }
      'jenkins.plugins.extracolumns.BuildDurationColumn' {
        buildDurationType 1
      }
      'jenkins.plugins.extracolumns.BuildDurationColumn' {
        buildDurationType 0
      }
      'jenkins.plugins.extracolumns.TestResultColumn' {
        testResultFormat 0
      }
    }
  }
}
