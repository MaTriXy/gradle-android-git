# Considerations

Considerations of design to make 'gag' a useful plugin.

----
## Sat. August 23, 2014

## Improvements

What point does this tool improve?

* We don't have to commit library code base to our app's repo.
* We can develop our own libraries with library-client apps because it's easy to switch library version.
* We can use many libraries which are not released on Maven central or jcenter.

## Prerequisites

### Git installation

Git must be installed on your machine.  
'gag' doesn't contain its own Git binary or `jgit` libs.  

### Gradle configured libraries

Although 'gag' enables to manage Git repos as the Gradle dependencies,  
building apps is still Gradle's responsibility.  
So libraries which are cloned by 'gag' must be configured with `build.gradle`.

## Names of the commands

### Avoid conflicts with other names

Keeping Android plugin commands' availability is the highest priority.  
So names which have prefixes like following should be avoided:

* assemble
* build
* install
* check
* connected

## Workflow

### Avoiding manual operations

It is necessary to avoid forcing manual operations to team members.

Although team leaders who manages app's dependencies should know about this plugin's spec or status of dependencies,  
members shouldn't care about any of these things.

Projects should be built with normal commands to lower the cost of learning.  
For example, following commands always should be executed successfully.

```sh
$ git clone YOUR_APP_REPO.git REPO
$ cd REPO
$ ./gradlew assemble
```

### Configure with codes, not operations

All of the configurations to manage dependencies should be managed by codes.  
Because people may forget to do some operations,  
which causes unstable environment,  
and unfortunately if it doesn't cause any build errors, maybe it will cause very different bugs.

So this is not good:

```sh
$ git clone YOUR_APP_REPO.git REPO
$ cd REPO

# Maybe forcing this step causes troubles...
$ ./gradlew initGagDependencies

$ ./gradlew assemble
```

Initialization commands should be automatically executed when they are needed.

### Always update dependencies

To updating dependencies(repositories) only when there are any updates is not good workflow.  
We will forget to do this.

Following steps always should be done for each dependencies when building apps:

```sh
# If the REPO doesn't exist:
$ git clone REPO

# Check out the target version.
# REPO should not be edited,
# so discarding changes will be appropriate operation
$ git checkout --force VERSION
```

So updating task should be inserted before `:preBuild` task or something like that.

### Add new dependency smoothly

Currently, following steps will be needed to introduce a new Git dependency.

1. Add `repo` configuration to `git.dependencies` closure.
1. Add `compile project(':library:foo')` to `project.dependencies`.
1. Add `include :library:foo` to `settings.gradle`.
This is necessary to evaluate dependencies for gradle but it is not supported to add sub-projects dynamically by plugins.

Gradle evaluates `settings.gradle` before the configuration phase,  
so plugin configuration initializers(constructors) should prepare for gradle to be recognized as valid sub-projects.  
Example:

1. Make its directories
1. Create minimum files to be recognized as a project(`build.gradle`)

Without these steps, projects can't be built after checking out the root project repository.

Perhaps the second of the above steps should be replaced to `git clone` or something.

----
## Sat. August 23, 2014

## Workflow

#### Issue in the case of adopting the 1 command model(#7)

Though initialization in separated command may be forgotten and not good in terms of the operations, it is necessary to do that because the job like `initGagDependencies` changes the structure of sub-projects, which must be re-evaluated by gradle. It means, it needs 2 build life-cycles and cannot be concatenated into 1 job.

The bad point pointed at the above is the commands are separated and they need some judgements by users.  
Users are forced to know when the commands should be executed and which command should be executed.

If we clear that point, it's not so bad to separate commands.

#### Auto-detect when to initialize/update sub-project

Plugin should detect when to change the sub-projects' structure.
In order to realize this, following actions must be executed.

1. Save the states of sub-projects. (repository location, commit, tag, ...)
1. Check if there are any differences of the states when the build command(`assemble` or `install`) is executed. (e.g. `commit` parameter in the `dependencies` closure has been changed)
1. If something changed, abort the build commands and prompt users to execute initialization/updating command.

#### Issue of build life-cycle(#7)

Creating temporary files to cheat gradle (Android plugin) doesn't solve the problem.  
Because if we define a temporary build.gradle in the Initialization phase or the Configuration phase and cloning/updating Git repos in the Execution phase, sub-projects' real build.gradle are not evaluated any more.  
So if we want to change the structure of sub-projects, we must separate jobs to configuration job and execution job.

## Another issue: library version(#6)

If we build dependencies as sub-projects and they are configured with `version` property in the `allprojects` closure, root project build will fail.

So maybe using Android libraries as sub-projects is bad model, they should be built as `aar`s and should be uploaded to local repository.

### Possible solutions

#### Manipulating `build.gradle` by plugin

Removing `version` configuration property in plugin may be a solution.
But it is difficult and dangerous.

#### Creating and uploading `aar` by plugin

Create `aar` and upload to Maven repository (inside the project) for each sub-projects.
If we can divide build job into two, maybe it's not so difficult.
But this will also need manipulation of `build.gradle`.

##### Build phase 1

1. Add `uploadArchive` task to `build.gradle` to upload our local project's repository. (`./.library/.repo`)
1. Execute `uploadArchive` (which depends on `assemble`)

##### Build phase 2

1. Execute normal build(`assemble`, `install`) with local Maven repo(`./.library/.repo`)

Since the path of the local repo(`./.library/.repo`) will be written to app project's `build.gradle`, maybe it must be created in the initialization/configuration phase.

## Adding uploadArchives to library

Adding `uploadArchives`, `mavenDeployer` to git dependencies may be a good idea.

### Experiments

This works.

Add following codes to `library/afe/androidformenhancer/build.gradle`:

```groovy
apply plugin: 'maven'
uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri('../../../../repo'))
            pom.groupId = "com.github.ksoichiro"
        }
    }
}
```

Then:

```sh
$ cd library/afe
$ ./gradlew :androidformenhancer:uploadArchives
```

This will copy `aar`s to `../../../../repo`.

And with following code on app project's `build.gradle`, we can use the above aar.

```groovy
repositories {
     maven {
         // local maven repo generated by gag
         url uri('../repo')
     }
     mavenCentral()
}

dependencies {
     compile 'com.github.ksoichiro:androidformenhancer:1.1.0'
}
```

### Considerations

* Creating local repo and uploading `aar`s to library will work.
* Adding above code is just appending to existing `build.gradle`, which seems not so difficult.
* But what if there is already an `uploadArchives` configuration?
* `gradlew` should be launched as a sub-process of the plugin. The plugin doesn't know if there are `gradlew` files or which should be executed(`gradlew` and `gradlew.bat`).
* Detecting OS may be [possible](http://stackoverflow.com/questions/11235614/how-to-detect-the-current-os-from-gradle).
* The next question is: does these mechanisms work immediately after checking out the app project?

----

## Experiment 2

Because of gradle trying to resolve all the dependencies written in `build.gradle`, we cannot put dependency initialization configuration and building app configuration together.

Initialize with another gradle file `init.gradle` may be a solution.

```groovy
buildscript {
    repositories {
        mavenCentral()
        // for testing plugin
        maven {
            url uri('../repo')
        }
    }
    dependencies {
        classpath 'com.github.ksoichiro:gradle-android-git:0.1.+'
    }
}

apply plugin: 'gag'

git {
    directory = "library"
    dependencies {
        // Use older version by commit hash (Detached HEAD)
        repo location: 'https://github.com/ksoichiro/AndroidFormEnhancer.git', name: 'afe', libraryProject: 'androidformenhancer', groupId: 'com.github.ksoichiro', commit: '5a9492f45fd0f97289001a7398d04c59b846af40'
        // Use older version by tag (Detached HEAD)
        repo location: 'https://github.com/ksoichiro/SimpleAlertDialog-for-Android.git', name: 'sad', libraryProject: 'simplealertdialog', groupId: 'com.github.ksoichiro', tag: 'v1.1.1'
    }
}
```

And `build.gradle` is simple:

```groovy
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:0.12.+'
    }
}

apply plugin: 'com.android.application'

android {
    compileSdkVersion 19
    buildToolsVersion "19.1.0"
    defaultConfig {
        minSdkVersion 8
    }
}

// Refer to Maven repo created by gag
repositories {
    maven {
        url uri('library/.repo')
    }
}

dependencies {
    compile 'com.android.support:support-v4:20.0.+'
    compile 'com.github.ksoichiro:androidformenhancer:1.1.0@aar'
    compile 'com.github.ksoichiro:simplealertdialog:1.1.1@aar'
}
```

Then do this:

```sh
$ ./gradlew -b init.gradle update
$ ./gradlew clean assemble
```

Without initialize command:

```sh
$ ./gradlew assemble
Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0

FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring root project 'consumer'.
> Could not resolve all dependencies for configuration ':_debugCompile'.
   > Could not find com.github.ksoichiro:androidformenhancer:1.1.0.
     Required by:
         :consumer:unspecified
   > Could not find com.github.ksoichiro:simplealertdialog:1.1.1.
     Required by:
         :consumer:unspecified

* Try:
Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.

BUILD FAILED

Total time: 4.881 secs
```

Now we can notice that there are something to do before executing `./gradlew assemble`.

Next question: what if there are updates in the library projects?

## Experiment 3

How about using commit hash or tag as a version number?

e.g. `library/afe/androidformenhancer/build.gradle`

```groovy
uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri('../../.repo'))
            pom.groupId = "com.github.ksoichiro"
            pom.version = "5a9492f45fd0f97289001a7398d04c59b846af40"
        }
    }
}
```

`build.gradle` of app:

```groovy
dependencies {
    compile 'com.android.support:support-v4:20.0.+'
    compile 'com.github.ksoichiro:androidformenhancer:5a9492f45fd0f97289001a7398d04c59b846af40@aar'
    compile 'com.github.ksoichiro:simplealertdialog:v1.1.1@aar'
}
```

Above codes also worked well.

When we need to update dependencies, update both `init.gradle` and `build.gradle`.  
In the following codes, `AndroidFormEnhancer`'s commit hashes have been changes from `5a9492f45fd0f97289001a7398d04c59b846af40` to `a9d4496adee3aa79e118c8db9ddd4a0fff1c03d9`.

`init.gradle`:

```groovy
git {
    directory = "library"
    dependencies {
        // Use older version by commit hash (Detached HEAD)
        repo location: 'https://github.com/ksoichiro/AndroidFormEnhancer.git', name: 'afe', libraryProject: 'androidformenhancer', groupId: 'com.github.ksoichiro', commit: 'a9d4496adee3aa79e118c8db9ddd4a0fff1c03d9'
        // Use older version by tag (Detached HEAD)
        repo location: 'https://github.com/ksoichiro/SimpleAlertDialog-for-Android.git', name: 'sad', libraryProject: 'simplealertdialog', groupId: 'com.github.ksoichiro', tag: 'v1.1.1'
    }
}
```

`build.gradle`:

```groovy
dependencies {
    compile 'com.android.support:support-v4:20.0.+'
    compile 'com.github.ksoichiro:androidformenhancer:a9d4496adee3aa79e118c8db9ddd4a0fff1c03d9@aar'
    compile 'com.github.ksoichiro:simplealertdialog:v1.1.1@aar'
}
```

With these codes and the local repo doesn't have the specified version aar, gradle can't resolve dependencies, which results in error.  
So the team members can notice that they need to update dependencies.

```sh
$ ./gradlew assemble
Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0

FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring root project 'consumer'.
> Could not resolve all dependencies for configuration ':_debugCompile'.
   > Could not find com.github.ksoichiro:androidformenhancer:a9d4496adee3aa79e118c8db9ddd4a0fff1c03d9.
     Required by:
         :consumer:unspecified

* Try:
Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.

BUILD FAILED
```

### Updating dependencies' version

If we see the error above, just execute a command below:

```sh
$ ./gradlew -b init.gradle update
```

If `init.gradle` is properly configured, the specified version will be installed to local repo(`library/.repo`).

Then, build again:

```sh
$ ./gradlew assemble
```
