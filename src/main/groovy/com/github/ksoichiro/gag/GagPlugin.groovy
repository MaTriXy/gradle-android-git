package com.github.ksoichiro.gag

import org.gradle.api.Plugin
import org.gradle.api.Project


class GagPlugin implements Plugin<Project> {
    void apply(Project target) {
        target.extensions.create("git", GagPluginExtension, target)
        target.git.extensions.create("dependencies", Dependencies, target)

        target.task('update', type: UpdateTask)
        target.task('joke', type: JokeTask)

        target.task('listConfig') << {
            target.git.dependencies.repos.each() { repo ->
                println "dependency:"
                println "  location: ${repo.location}"
                println "  name: ${repo.name}"
                println "  commit: ${repo.commit}"
                println "  tag: ${repo.tag}"
            }
        }
    }
}

class GagPluginExtension {
    Project project

    GagPluginExtension(Project project) {
        this.project = project
    }
}

class Dependencies {
    Project project
    List<Repo> repos = []

    Dependencies(Project project) {
        this.project = project
    }

    void repo(Map<String, ?> map) {
        def r = new Repo()
        project.configure(r) {
            location = map["location"]
            name = map["name"]
            commit = map["commit"]
            tag = map["tag"]
        }
        repos.add(r)
    }

    void repo(Closure closure) {
        def r = new Repo()
        project.configure(r, closure)
        repos.add(r)
    }
}

class Repo {
    String location
    String name
    String commit
    String tag
}
