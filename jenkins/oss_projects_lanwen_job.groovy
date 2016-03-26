def JACOCO_VER = '0.7.5.201505241946'
def CREDS_ID = 'b4a9fdbe-64cd-4c72-9c73-686b177c40ce'
def projects = [
  'VerbalExpressions/JavaVerbalExpressions',
  'yandex-qatools/uri-differ'
]

listView('OSS Projects') {
    jobs {
        regex('oss_.*')
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
    mavenJob('oss_' + project.replace('/', '-') + '_master-deploy') {
        logRotator {
            numToKeep(5)
            artifactNumToKeep(2)
        }
        label('master')
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
  
        wrappers {
            sshAgent(CREDS_ID)
            mavenRelease {
                releaseGoals('release:clean release:prepare release:perform')
                dryRunGoals('-DdryRun=true release:prepare')
                numberOfReleaseBuildsToKeep(10)
            }
        }
      
        triggers {
            githubPush()
            scm('H */5 * * *')
        }

        goals("org.jacoco:jacoco-maven-plugin:${JACOCO_VER}:prepare-agent clean deploy")

        publishers {
            sonar()
            jacocoCodeCoverage()
        }
}
}

projects.each { project ->
    mavenJob('oss_' + project.replace('/', '-') + '_pr-test') {
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
        
        publishers {
            jacocoCodeCoverage()
        }
    }
}
