def projects = [
        'jenkinsci/github-pullrequest-plugin',
        'jenkinsci/github-plugin',
        'jenkinsci/docker-plugin',
        'jenkinsci/envinject-plugin'
]

def JACOCO_VER = "0.7.5.201505241946"
def CREDS_ID = 'b4a9fdbe-64cd-4c72-9c73-686b177c40ce'

listView('Jenkins Plugins') {
    jobs {
        regex('.*jenkinsci.*')
    }
    columns {
        status()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

projects.each { project ->
    mavenJob(project.replace('/', '-') + '_master-test-release') {
        label('master')
        logRotator {
            numToKeep(5)
            artifactNumToKeep(2)
        }
        scm {
            git {
                remote {
                    github(project, 'ssh', 'github.com')
                    credentials(CREDS_ID)
                }
                branch('master')
                extensions {
                    localBranch('master')
                }
            }
        }

        triggers {
            githubPush()
            scm('H */2 * * *')
        }

        wrappers {
            configFiles {
                mavenSettings('jenkins_release_settings') {
                    variable('SETTINGS_LOCATION')
                }
            }
            sshAgent(CREDS_ID)
            mavenRelease {
                releaseGoals('-s ${SETTINGS_LOCATION} -P jenkins release:clean release:prepare release:perform')
                dryRunGoals('-DdryRun=true -s ${SETTINGS_LOCATION} release:clean release:prepare -P jenkins')
                numberOfReleaseBuildsToKeep(10)
            }
        }

        goals("-s " + '${SETTINGS_LOCATION}' + " -P jenkins org.jacoco:jacoco-maven-plugin:${JACOCO_VER}:prepare-agent clean install")

        postBuildSteps {
            maven {
                goals('$SONAR_MAVEN_GOAL')
                property('sonar.host.url', '$SONAR_HOST_URL')
                mavenOpts('-Xmx1024m -Xms256m')
            }
        }
        
        configure { job ->
            job / buildWrappers << 'hudson.plugins.sonar.SonarBuildWrapper'()
        }
    }
}

projects.each { project ->
    if(project.equals('jenkinsci/envinject-plugin')) {
        return
    }
    mavenJob(project.replace('/', '-') + '_pr-test') {
        label('master')
        logRotator {
            numToKeep(5)
            artifactNumToKeep(2)
        }
        scm {
            git {
                remote {
                    github(project, 'ssh', 'github.com')
                    credentials(CREDS_ID)
                    refspec('+refs/pull/${GITHUB_PR_NUMBER}/${GITHUB_PR_COND_REF}:refs/remotes/origin/pull/${GITHUB_PR_NUMBER}/${GITHUB_PR_COND_REF}')
                }
                branch('pull/${GITHUB_PR_NUMBER}/${GITHUB_PR_COND_REF}')
            }
        }

        triggers {
            onPullRequest {
                cron('H/5 * * * *')
                mode {
                    cron()
                }
                events {
                    opened()
                    commit()
                }
            }
        }

        goals("org.jacoco:jacoco-maven-plugin:${JACOCO_VER}:prepare-agent clean install")
        
        postBuildSteps {
            maven {
                goals('$SONAR_MAVEN_GOAL $SONAR_EXTRA_PROPS')
                properties(
                    'sonar.host.url': '$SONAR_HOST_URL',
                    'sonar.analysis.mode': 'incremental',
                    'sonar.github.pullRequest': '$GITHUB_PR_NUMBER',
                    'sonar.github.repository': project
                )
                mavenOpts('-Xmx1024m -Xms256m')
            }
        }
  
        configure { job ->
            job / buildWrappers << 'hudson.plugins.sonar.SonarBuildWrapper'()
        }
    }
}
