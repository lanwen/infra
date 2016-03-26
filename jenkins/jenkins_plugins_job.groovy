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
                localBranch('master')
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

        publishers {
            sonar()
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
//                setPreStatus()
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

        configure { job ->
            job / publishers << 'hudson.plugins.sonar.SonarPublisher' {
                jdk('(Inherit From Job)')
                branch()
                language()
                mavenOpts("-Xmx1024m -Xms256m")
                jobAdditionalProperties('-Dsonar.analysis.mode=incremental -Dsonar.github.pullRequest=$GITHUB_PR_NUMBER -Dsonar.github.repository=' + project)
                settings(class: 'jenkins.mvn.DefaultSettingsProvider')
                globalSettings(class: 'jenkins.mvn.DefaultGlobalSettingsProvider')
                usePrivateRepository(false)
            }
        }
        
        publishers {
//            commitStatusOnGH {
//                unstableAsError()
//                message('Build finished')
//            }
        }
    }
}
