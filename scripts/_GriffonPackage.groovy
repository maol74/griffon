/*
* Copyright 2004-2005 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.log4j.LogManager
import org.codehaus.griffon.commons.*
import org.codehaus.griffon.plugins.logging.Log4jConfig
//import org.codehaus.griffon.commons.cfg.ConfigurationHelper
//import org.springframework.core.io.FileSystemResource

/**
 * Gant script that packages a Griffon application (note: does not create WAR)
 *
 * @author Graeme Rocher
 * @author Danno Ferrin
 *
 * @since 0.4
 */


import org.codehaus.griffon.util.BuildSettings
import static griffon.util.GriffonApplicationUtils.isLinux
import static griffon.util.GriffonApplicationUtils.isSolaris
import static griffon.util.GriffonApplicationUtils.isMacOSX

includeTargets << griffonScript("_GriffonCompile")
includeTargets << griffonScript("_PackagePlugins")

configTweaks = []

target( createConfig: "Creates the configuration object") {
   if(!ConfigurationHolder.config && configFile.exists()) {
       def configClass
       try {
           configClass = classLoader.loadClass("Config")
       } catch (ClassNotFoundException cnfe) {
           println "WARNING: No config found for the application."
       }
       if(configClass) {
           try {
               config = configSlurper.parse(configClass)
               config.setConfigFile(configFile.toURI().toURL())

               ConfigurationHolder.setConfig(config)
           }
           catch(Exception e) {
               logError("Failed to compile configuration file",e)
               exit(1)
           }
       }
       griffonAppletClass = config.griffon.applet.mainClass ?: defaultGriffonAppletClass
       griffonApplicationClass = config.griffon.application.mainClass ?: defaultGriffonApplicationClass
   }
    if (applicationFile.exists()) {
        def applicationConfigClass
        try {
            applicationConfigClass = classLoader.loadClass("Application")
        } catch (ClassNotFoundException cnfe) {
            println "WARNING: No Application.groovy found for the application."
        }
        if (applicationConfigClass) {
            try {
                applicationConfig = configSlurper.parse(applicationConfigClass)
                applicationConfig.setConfigFile(applicationFile.toURI().toURL())

                //ConfigurationHolder.setConfig(config)
            }
            catch(Exception e) {
                logError("Failed to compile Application configuration file", e)
                exit(1)
            }
        }
    }
//   def dataSourceFile = new File("${basedir}/griffon-app/conf/DataSource.groovy")
//   if(dataSourceFile.exists()) {
//        try {
//           def dataSourceConfig = configSlurper.parse(classLoader.loadClass("DataSource"))
//           config.merge(dataSourceConfig)
//           ConfigurationHolder.setConfig(config)
//        }
//        catch(ClassNotFoundException e) {
//            println "WARNING: DataSource.groovy not found, assuming dataSource bean is configured by Spring..."
//        }
//        catch(Exception e) {
//            logError("Error loading DataSource.groovy",e)
//            exit(1)
//        }
//   }
//   ConfigurationHelper.initConfig(config, null, classLoader)

    configTweaks.each {tweak -> tweak() }
}

target( packageApp : "Implementation of package target") {
    depends(createStructure, packagePlugins)

    try {
        profile("compile") {
            compile()
        }
    }
    catch(Exception e) {
        logError("Compilation error",e)
        exit(1)
    }
    profile("creating config") {
        createConfig()
    }

    // flag if <application>.jar is up to date
    jardir = ant.antProject.replaceProperties(config.griffon.jars.destDir)
    ant.uptodate(property:'appJarUpToDate', targetfile:"${jardir}/${config.griffon.jars.jarName}") {
        srcfiles(dir:"${basedir}/griffon-app/", includes:"**/*")
        srcfiles(dir:"$classesDirPath", includes:"**/*")
    }

    platform = 'windows'
    if(isSolaris) platform = 'solaris'
    else if(isLinux) platform = 'linux'
    else if(isMacOSX) platform = 'macosx'

//    configureServerContextPath()

    i18nDir = "${resourcesDirPath}/griffon-app/i18n"
    ant.mkdir(dir:i18nDir)

    resourcesDir = "${resourcesDirPath}/griffon-app/resources"
    ant.mkdir(dir:resourcesDir)

    collectArtifactMetadata()

//    def files = ant.fileScanner {
//        fileset(dir:"${basedir}/griffon-app/views", includes:"**/*.jsp")
//    }
//
//    if(files.iterator().hasNext()) {
//        ant.mkdir(dir:"${basedir}/web-app/WEB-INF/griffon-app/views")
//        ant.copy(todir:"${basedir}/web-app/WEB-INF/griffon-app/views") {
//            fileset(dir:"${basedir}/griffon-app/views", includes:"**/*.jsp")
//        }
//    }

    if(config.griffon.enable.native2ascii) {
        profile("converting native message bundles to ascii") {
            ant.native2ascii(src:"${basedir}/griffon-app/i18n",
                             dest:i18nDir,
                             includes:"*.properties",
                             encoding:"UTF-8")
        }
    }
    else {
        ant.copy(todir:i18nDir) {
            fileset(dir:"${basedir}/griffon-app/i18n", includes:"*.properties")
        }
    }
    ant.copy(todir:resourcesDir) {
        fileset(dir:"${basedir}/griffon-app/resources", includes:"**/*.*")
        fileset(dir:"${basedir}/src/main") {
            include(name:"**/*")
            exclude(name:"**/*.java")
            exclude(name:"**/*.groovy")
        }
    }
    ant.copy(todir:classesDirPath) {
        fileset(dir:"${basedir}", includes:metadataFile.name)
    }
    ant.copy(todir:resourcesDirPath, failonerror:false) {
        fileset(dir:"${basedir}/griffon-app/conf", includes:"**", excludes:"*.groovy, log4j*, webstart"/*hibernate, spring"*/)
//        fileset(dir:"${basedir}/griffon-app/conf/hibernate", includes:"**/**")
//        fileset(dir:"${basedir}/src/java") {
        fileset(dir:"${basedir}/src/main") {
            include(name:"**/**")
            exclude(name:"**/*.java")
            exclude(name:"**/*.groovy")
        }
    }


    startLogging()

    loadPlugins()
    //generateWebXml()

    checkKey()
    copyLibs()
    jarFiles()

    event("PackagingEnd",[])
}

collectArtifactMetadata = {
    def artifactPaths = [
        [type: "model",      path: "models",      suffix: "Model"],
        [type: "view",       path: "views",       suffix: "View"],
        [type: "controller", path: "controllers", suffix: "Controller"],
        [type: "service",    path: "services",    suffix: "Service"]
    ]

    event("CollectArtifacts", [artifactPaths])

    def artifacts = [:]
    def searchPath = new File("${basedir}/griffon-app")
    searchPath.eachFileRecurse { file ->
        artifactPaths.find { entry ->
            def fixedPath = file.path - searchPath.canonicalPath //fix problem when project inside dir "jobs" (eg. hudson stores projects under jobs-directory)
            if(fixedPath =~ entry.path && file.isFile()) {
                def klass = fixedPath.substring(2 + entry.path.size()).replace(File.separator,".")
                klass = klass.substring(0, klass.lastIndexOf("."))
                if(entry.suffix) {
                    if(klass.endsWith(entry.suffix)) artifacts.get(entry.type, []) << klass
                } else {
                    artifacts.get(entry.type, []) << klass
                }
            }
        }
    }

    def artifactMetadataFile = new File("${resourcesDirPath}/griffon-app/resources/artifacts.properties")
    artifactMetadataFile.withPrintWriter { writer ->
        artifacts.each { type, list ->
           writer.println("$type = '${list.join(',')}'")
        }
    }
}

target(checkKey: "Check to see if the keystore exists")  {
    if (config.griffon.jars.sign) {
        // check for passwords
        // pw is echoed, but jarsigner does that too...
        // when we go to 1.6 only we should use java.io.Console
        if (!config.signingkey.params.storepass) {
            print "Enter the keystore password:"
            config.signingkey.params.storepass = System.in.newReader().readLine()
        }
        if (!config.signingkey.params.keypass) {
            print "Enter the key password [blank if same as keystore] :"
            config.signingkey.params.keypass = System.in.newReader().readLine() ?: config.signingkey.params.storepass
        }

        if (!(new File(ant.antProject.replaceProperties(config.signingkey.params.keystore)).exists())) {
            println "Auto-generating a local self-signed key"
            Map genKeyParams = [:]
            genKeyParams.dname =  'CN=Auto Gen Self-Signed Key -- Not for Production, OU=Development, O=Griffon'
            for (key in ['alias', 'storepass', 'keystore', 'storetype', 'keypass', 'sigalg', 'keyalg', 'verbose', 'dname', 'validity', 'keysize']) {
                if (config.signingkey.params."$key") {
                    genKeyParams[key] = config.signingkey.params[key]
                }
            }
            ant.genkey(genKeyParams)
        }
    }
}

target(jarFiles: "Jar up the package files") {
    if(argsMap['jar']) return
    boolean upToDate = ant.antProject.properties.appJarUpToDate
    ant.mkdir(dir:jardir)

    String destFileName = "$jardir/${config.griffon.jars.jarName}"
    if (!upToDate) {
        ant.jar(destfile:destFileName) {
            fileset(dir:classesDirPath) {
                exclude(name:'Config*.class')
            }
            fileset(dir:i18nDir)
            fileset(dir:resourcesDir)
        }
    }
    griffonCopyDist(destFileName, jardir, !upToDate)
}

target(copyLibs: "Copy Library Files") {
    jardir = ant.antProject.replaceProperties(config.griffon.jars.destDir)
    event("CopyLibsStart", [jardir])

    fileset(dir:"${griffonHome}/dist", includes:"griffon-rt-*.jar").each {
        griffonCopyDist(it.toString(), jardir)
    }
    fileset(dir:"${griffonHome}/lib", includes:"groovy-all-*.jar").each {
        griffonCopyDist(it.toString(), jardir)
    }

    fileset(dir:"${basedir}/lib/", includes:"*.jar").each {
        griffonCopyDist(it.toString(), jardir)
    }

    copyPlatformJars("${basedir}/lib", jardir)
    
//FIXME    ant.copy(todir:jardir) { fileset(dir:"${basedir}/lib/", includes:"*.dll") }
//FIXME    ant.copy(todir:jardir) { fileset(dir:"${basedir}/lib/", includes:"*.so") }

    event("CopyLibsEnd", [jardir])
}

/**
 * The presence of a .SF, .DSA, or .RSA file in meta-inf means yes
 */
boolean isJarSigned(File jarFile, File targetFile) {
    File fileToSearch  = targetFile.exists() ? targetFile : jarFile;

    ZipFile zf = new ZipFile(fileToSearch)
    try {
        // don't use .each {}, cannot break out of closure
        Enumeration<ZipEntry> entriesEnum = zf.entries()
        while (entriesEnum.hasMoreElements()) {
            ZipEntry ze = entriesEnum.nextElement()
            if (ze.name ==~ 'META-INF/\\w{1,8}\\.(SF|RSA|DSA)') {
                // found a signature file
                return true
            }
            // possible optimization, expect META-INF first?  stop looking when we see other dirs?
        }
        // found no signature files
        return false
    } finally {
        zf.close()
    }
}

griffonCopyDist =  { jarname, targetDir, boolean force = false ->
    File srcFile = new File(jarname);
    if (!srcFile.exists()) {
        event("StatusFinal", ["Source jar does not exist: ${srcFile.getName()}"])
        exit(1)
    }
    File targetFile = new File(targetDir + File.separator + srcFile.getName());

    // first do a copy
    long originalLastMod = targetFile.lastModified()
    force = force || !(config.signingkey?.params?.lazy)

    ant.copy(file:srcFile, toFile:targetFile, overwrite:force)

    maybePackAndSign(srcFile, targetFile, force)
}

maybePackAndSign = {srcFile, targetFile = srcFile, boolean force = false ->


    // we may already be copied, but not packed or signed
    // first see if the config calls for packing or signing
    // (do this funny dance because unset == true)
    boolean configSaysJarPacking = config.griffon.jars.pack
    boolean configSaysJarSigning = config.griffon.jars.sign

    boolean doJarSigning = configSaysJarSigning
    boolean doJarPacking = configSaysJarPacking

    // if we should sign, check if the jar is already signed
    // don't sign if it appears signed and we're not forced
    if (doJarSigning && !force) {
        doJarSigning = !isJarSigned(srcFile, targetFile)
    }

    // if we should pack, check for forcing or a newer .pack.gz file
    // don't pack if it appears newer and we're not forced
    if (doJarPacking && !force) {
        doJarPacking = !new File(targetFile.path + ".pack.gz").exists()
    }

    // packaging quirk, if we sign or pack, we must do both if either calls for a re-do
    doJarSigning = doJarSigning || (configSaysJarSigning && doJarPacking)
    doJarPacking = doJarPacking || (configSaysJarPacking && doJarSigning)

    //TODO strip old signatures?

    def packOptions = [
        '-mlatest', // smaller files, set modification time on the files to latest
        '-Htrue', // smaller files, always use DEFLATE hint
        '-O', // smaller files, reorder files if it makes things smaller
    ]


    def signJarParams = [:]
    // prep sign jar params
    if (doJarSigning) {
        // sign jar
        for (key in ['alias', 'storepass', 'keystore', 'storetype', 'keypass', 'sigfile', 'verbose', 'internalsf', 'sectionsonly', 'lazy', 'maxmemory', 'preservelastmodified', 'tsaurl', 'tsacert']) {
            if (config.signingkey.params."$key") {
                signJarParams[key] = config.signingkey.params[key]
            }
        }
        signJarParams.jar = targetFile.path
    }

    // repack so we can sign pack200
    if (doJarPacking) {
        ant.exec(executable:'pack200') {
            for (option in packOptions) {
                arg(value:option)
            }
            arg(value:'--repack')
            arg(value:targetFile)
        }
    }

    // sign before packing to create accurage space
    if (doJarSigning && doJarPacking) {
        ant.signjar(signJarParams)
    }

    
    // repack so we can sign pack200
    if (doJarPacking) {
        ant.exec(executable:'pack200') {
            for (option in packOptions) {
                arg(value:option)
            }
            arg(value:'--repack')
            arg(value:targetFile)
        }
    }

    // sign jar for real
    if (doJarSigning) {
        ant.signjar(signJarParams)
    }

    // pack jar for real
    if (doJarPacking) {
        ant.exec(executable:'pack200') {
            for (option in packOptions) {
                arg(value:option)
            }
            arg(value:"${targetFile}.pack.gz")
            arg(value:targetFile)
        }

        //TODO? validate packed jar is signed properly

        //TODO? versioning
        // check for version number
        //   copy to version numberd file if version # available

    }

    return targetFile
}

target(generateJNLP:"Generates the JNLP File") {
    ant.copy (todir:jardir, overwrite:true) {
        fileset(dir:"${basedir}/griffon-app/conf/webstart")
    }

    jnlpJars = []
    jnlpUrls = []
    jnlpExtensions = []
    jnlpResources = []
    appletJars = []
    remoteJars = []
    config.griffon.extensions?.jarUrls.each {
        def filename = new File(it).getName()
        remoteJars << filename
    }
    // griffon-rt has to come first, it's got the launch classes
    new File(jardir).eachFileMatch(~/griffon-rt-.*.jar/) { f ->
        jnlpJars << "        <jar href='$f.name'/>"
        appletJars << "$f.name"
    }
    config.griffon.extensions?.jarUrls.each {
        appletJars << it
    }
    if (config.griffon.extensions?.jnlpUrls.size() > 0) {
        config.griffon.extensions?.jnlpUrls.each {
            jnlpExtensions << "<extension href='$it' />"
        }
    }
    new File(jardir).eachFileMatch(~/.*\.jar/) { f ->
        if (!(f.name =~ /griffon-rt-.*/) && !remoteJars.contains(f.name)) {
            jnlpJars << "        <jar href='$f.name'/>"
            appletJars << "$f.name"
        }
    }
    if (config.griffon.extensions?.resources?.size() > 0) {
        config.griffon.extensions?.resources.each { entry ->
            jnlpResources << "<resources"
            if(entry.os) jnlpResources << " os='${entry.os}'" 
            if(entry.arch) jnlpResources << " arch='${entry.arch}'" 
            jnlpResources << ">"
            for(j in entry.jars) jnlpResources << "    <jar href='$j' />"
            for(l in entry.nativelibs) jnlpResources << "    <nativelib href='$l' />"
            jnlpResources << "</resources>"
        }
    }
    doForAllPlatforms { platformDir, platformOs ->
        if(platformDir.list()) {
            jnlpResources << "<resources os='${platformOs}'>"
            platformDir.eachFileMatch(~/.*\.jar/) { f ->
                jnlpResources << "    <jar href='${platformOs}/${f.name}' />"
            }
            jnlpResources << "</resources>"
        }
    }

    memOptions = []
    if (config.griffon.memory?.min) {
        memOptions << "initial-heap-size='$config.griffon.memory.min'"
    }
    if (config.griffon.memory?.max) {
        memOptions << "max-heap-size='$config.griffon.memory.max'"
    }
    if (config.griffon.memory?.maxPermSize) {
        // may be fragile
        memOptions << "java-vm-args='-XX:maxPermSize=$config.griffon.memory.maxPermSize'"
    }

    doPackageTextReplacement(jardir, "*.jnlp,*.html")
}

doPackageTextReplacement = {dir, fileFilters ->
    ant.fileset(dir:dir, includes:fileFilters).each {
        String fileName = it.toString()
        ant.replace(file: fileName) {
            replacefilter(token:"@griffonAppletClass@", value: griffonAppletClass)
            replacefilter(token:"@griffonApplicationClass@", value: griffonApplicationClass)
            replacefilter(token:"@griffonAppName@", value:"${griffonAppName}" )
            replacefilter(token:"@griffonAppVersion@", value:"${griffonAppVersion}" )
            replacefilter(token:"@griffonAppCodebase@", value:"${config.griffon.webstart.codebase}")
            replacefilter(token:"@jnlpFileName@", value: new File(fileName).name )
            replacefilter(token:"@jnlpJars@", value:jnlpJars.join('\n') )
            replacefilter(token:"@jnlpExtensions@", value:jnlpExtensions.join('\n'))
            replacefilter(token:"@jnlpResources@", value:jnlpResources.join('\n'))
            replacefilter(token:"@appletJars@", value:appletJars.join(',') )
            replacefilter(token:"@memoryOptions@", value:memOptions.join(' ') )
        }
    }
}

//
//target(configureServerContextPath: "Configuring server context path") {
//    // Get the application context path by looking for a property named 'app.context' in the following order of precedence:
//    //    System properties
//    //    application.properties
//    //    config
//    //    default to griffonAppName if not specified
//
//    serverContextPath = System.getProperty("app.context")
//    serverContextPath = serverContextPath ?: metadata.'app.context'
//    serverContextPath = serverContextPath ?: config.griffon.app.context
//    serverContextPath = serverContextPath ?: griffonAppName
//
//    if(!serverContextPath.startsWith('/')) {
//        serverContextPath = "/${serverContextPath}"
//    }
//}
//

target(startLogging:"Bootstraps logging") {
    LogManager.resetConfiguration()
    if(config.log4j instanceof Closure) {
        profile("configuring log4j") {
            new Log4jConfig().configure(config.log4j)
        }
    }
    else {
        // setup default logging
        new Log4jConfig().configure()
    }
}

//target( generateWebXml : "Generates the web.xml file") {
//    depends(classpath)
//
//    if(config.griffon.config.base.webXml) {
//        def customWebXml =resolveResources(config.griffon.config.base.webXml)
//        if(customWebXml)
//            webXml = customWebXml[0]
//        else {
//            event("StatusError", [ "Custom web.xml defined in config [${config.griffon.config.base.webXml}] could not be found." ])
//            exit(1)
//        }
//    }
//    else {
//        webXml = new FileSystemResource("${basedir}/src/templates/war/web.xml")
//        def tmpWebXml = "${projectWorkDir}/web.xml.tmp"
//        if(!webXml.exists()) {
//            copyGriffonResource(tmpWebXml, griffonResource("src/war/WEB-INF/web${servletVersion}.template.xml"))
//        }
//        else {
//            ant.copy(file:webXml.file, tofile:tmpWebXml, overwrite:true)
//        }
//        webXml = new FileSystemResource(tmpWebXml)
//        ant.replace(file:tmpWebXml, token:"@griffon.project.key@", value:"${griffonAppName}-${griffonEnv}-${griffonAppVersion}")
//    }
//    def sw = new StringWriter()
//
//    try {
//        profile("generating web.xml from $webXml") {
//            event("WebXmlStart", [webXml.filename])
//            pluginManager.doWebDescriptor(webXml, sw)
//            webXmlFile.withWriter {
//                it << sw.toString()
//            }
//            event("WebXmlEnd", [webXml.filename])
//        }
//    }
//    catch(Exception e) {
//        logError("Error generating web.xml file",e)
//        exit(1)
//    }
//
//}

//target(packageTemplates: "Packages templates into the app") {
//    ant.mkdir(dir:scaffoldDir)
//    if(new File("${basedir}/src/templates/scaffolding").exists()) {
//        ant.copy(todir:scaffoldDir, overwrite:true) {
//            fileset(dir:"${basedir}/src/templates/scaffolding", includes:"**")
//        }
//    }
//    else {
//        copyGriffonResources(scaffoldDir, "src/griffon/templates/scaffolding/*")
//    }
//}
//
//
//// Checks whether the project's sources have changed since the last
//// compilation, and then performs a recompilation if this is the case.
//// Returns the updated 'lastModified' value.
//recompileCheck = { lastModified, callback ->
//    try {
//        def ant = new AntBuilder()
//        def classpathId = "griffon.compile.classpath"
//        ant.taskdef (name: 'groovyc', classname : 'org.codehaus.griffon.compiler.GriffonCompiler')
//        ant.path(id:classpathId,compileClasspath)
//
//        ant.groovyc(destdir:classesDirPath,
//                    classpathref:classpathId,
//                    encoding:"UTF-8",
//                    projectName:baseName) {
//                    src(path:"${basedir}/src/groovy")
//                    src(path:"${basedir}/griffon-app/domain")
//                    src(path:"${basedir}/griffon-app/utils")
//                    src(path:"${basedir}/src/java")
//                    javac(classpathref:classpathId, debug:"yes", target: '1.5')
//
//                }
//        ant = null
//    }
//    catch(Exception e) {
//        compilationError = true
//        logError("Error automatically restarting container",e)
//    }
//
//    def tmp = classesDir.lastModified()
//    if(lastModified < tmp) {
//
//        // run another compile JIT
//        try {
//            callback()
//        }
//        catch(Exception e) {
//            logError("Error automatically restarting container",e)
//        }
//
//        finally {
//           lastModified = classesDir.lastModified()
//        }
//    }
//
//    return lastModified
//}

PLATFORMS = [
    windows: [
        nativelib: '.dll',
        webstartName: 'Windows',
        archs: ['x86', 'amd64', 'x86_64']],
    linux: [
        nativelib: '.so',
        webstartName: 'Linux',
        archs: ['i386', 'x86', 'amd64', 'x86_64']],
    macosx: [
        nativelib: '.jnilib',
        webstartName: 'Mac OS X',
        archs: ['i386', 'ppc', 'x86_64']],
    solaris: [
        nativelib: '.so',
        webstartName: 'SunOS',
        archs: ['x86', 'amd64', 'x86_64', 'sparc', 'sparcv9']]
]

copyPlatformJars = { srcdir, destdir ->
    def env = System.getProperty(BuildSettings.ENVIRONMENT)
    if(env == BuildSettings.ENV_DEVELOPMENT) {
        _copyPlatformJars(srcdir.toString(), destdir.toString(), platform)
    } else {
        PLATFORMS.each { entry ->
            _copyPlatformJars(srcdir.toString(), destdir.toString(), entry.key)
        }
    }
}

_copyPlatformJars = { srcdir, destdir, os ->
    File src = new File(srcdir + File.separator + os)
    File dest = new File(destdir + File.separator + os)
    if(src.exists()) {
        ant.mkdir(dir: dest)
        src.eachFileMatch(~/.*\.jar/) { jarfile ->
            griffonCopyDist(jarfile.toString(), dest.toString())
        }
    }
}

copyNativeLibs = { srcdir, destdir ->
    def env = System.getProperty(BuildSettings.ENVIRONMENT)
    if(env == BuildSettings.ENV_DEVELOPMENT) {
        _copyNativeLibs(srcdir.toString(), destdir.toString(), platform)
    } else {
        PLATFORMS.each { entry ->
            _copyNativeLibs(srcdir.toString(), destdir.toString(), entry.key)
        }
    }
}

_copyNativeLibs = { srcdir, destdir, os ->
    File src = new File([srcdir, os, 'native'].join(File.separator))
    File dest = new File([destdir, os, 'native'].join(File.separator))
    if(src.exists()) {
        ant.mkdir(dir: dest)
        src.eachFileMatch(~/.*${PLATFORMS[os].nativelib}/) { srcFile ->
            File targetFile = new File(dest.absolutePath + File.separator + srcFile.name)
            ant.copy(file: srcFile, toFile: targetFile, overwrite: true)
        }
    }
}

doForAllPlatforms = { callback ->
    PLATFORMS.each { platformKey, platformValue ->
        def platformDir = new File(jardir, platformKey)
        if(callback && platformDir.exists()) {
            callback(platformDir, platformKey)
        }
    }
}
