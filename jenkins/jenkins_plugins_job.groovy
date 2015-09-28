def projects = [
        'jenkinsci/github-pullrequest-plugin',
        'jenkinsci/github-plugin',
        'jenkinsci/docker-plugin',
        'jenkinsci/envinject-plugin'
]

def JACOCO_VER = "0.7.5.201505241946"

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
    mavenJob(project.replace('/', '-') + '_master-test') {
        label('master')
        scm {
            git {
                remote {
                    github(project, 'ssh', 'github.com')
                    credentials('b4a9fdbe-64cd-4c72-9c73-686b177c40ce')
                }
                branch('master')
                localBranch('master')
            }
        }

        triggers {
            githubPush()
            scm('H/10 * * * *')
        }

        goals("org.jacoco:jacoco-maven-plugin:${JACOCO_VER}:prepare-agent clean install")

        publishers {
            sonar()
        }
    }
}

projects.each { project ->
    mavenJob(project.replace('/', '-') + '_pr-test') {
        label('master')
        scm {
            git {
                remote {
                    github(project, 'ssh', 'github.com')
                    credentials('b4a9fdbe-64cd-4c72-9c73-686b177c40ce')
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
