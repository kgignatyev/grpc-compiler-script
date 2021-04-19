package com.kgi;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

import java.io.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Mojo(name = "generate-build-script", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GrpcBuildScriptMojo extends AbstractMojo {

    String classpathPrefix = "classpath://";
    String filePrefix = "file://";


    @Parameter(required = false, defaultValue = "compile-grpc.sh")
    private String scriptName;

    @Parameter(required = true, defaultValue = "my-org")
    private String npmPrefix;


    @Parameter(required = false, defaultValue = "target/npm")
    private String npmOut;


    @Parameter(required = false, defaultValue = "classpath:///grpc-script-template.sh")
    private String scriptTemplateLocation;

    @Parameter(required = false, defaultValue = "classpath:///package-template.json")
    private String packageJsonTemplateLocation;

    @Parameter(required = false, defaultValue = "classpath:///info-template.sh")
    private String infoTemplateLocation;

    @Parameter(required = false, defaultValue = "classpath:///go.mod.template")
    private String goModTemplateLocation;

    @Parameter(required = false, defaultValue = "classpath:///main.go.template")
    private String goMainTemplateLocation;

    @Parameter(required = false, defaultValue = "classpath:///tools.go.template")
    private String goToolsTemplateLocation;

    @Parameter(required = false, defaultValue = "classpath:///go-build-template.sh")
    private String goBuildTemplateLocation;


    public void execute() throws MojoExecutionException {
        try {
            MavenProject project = (MavenProject) getPluginContext().get("project");
            File outputDirectory = project.getBasedir();
            File srcDir = new File(project.getBuild().getSourceDirectory());
            File protosSrc = new File(srcDir.getParentFile(), "proto");
            int indexOfProtosRoot = protosSrc.getAbsolutePath().length() + 1;
            List<String> protoFiles = FileUtils.getFileNames(protosSrc, "**/*.proto", null, true);
//            File[] protoFiles = protosSrc.listFiles((dir, name) -> name.endsWith(".proto"));
            List<String> protos = protoFiles.stream().map(f -> f.substring(indexOfProtosRoot)).collect(Collectors.toList());


            String protoSrcDir = "src/main/proto";

            System.out.println("outputDirectory = " + outputDirectory.getAbsolutePath());

            List<Dependency> dependencyList = project.getDependencies();
            List<Dependency> interfaceDeps = dependencyList.stream()
                    .filter(d -> d.getArtifactId().toLowerCase().endsWith("-interface")).collect(Collectors.toList());
            for (Dependency d : interfaceDeps) {
                System.out.println("d = " + d);
            }

            List npmDependencies = interfaceDeps.stream()
                    .filter( dependency -> dependency.getGroupId().equals( project.getGroupId()))
                    .collect(Collectors.toList());
            File mainProtoFile = new File(protosSrc, protos.get(0));
            List<String> protoLines = IOUtils.readLines( new FileReader(mainProtoFile));
            Context hbCxt = Context.newContext("grpc");
            hbCxt.data("PROTO_SRC_DIR", protoSrcDir);
            hbCxt.data("TS_OUT_DIR", npmOut);
            hbCxt.data("PROTOS", String.join(" ", protos));
            hbCxt.data("INTERFACE_VERSION", project.getVersion());
            hbCxt.data("INTERFACE_NAME", project.getArtifactId());
            hbCxt.data("NPM_PREFIX", npmPrefix);
            hbCxt.data("NPM_DEPENDENCIES", npmDependencies);
            hbCxt.data("PROTO_NAME", nameOfFirstProto( protos ));
            hbCxt.data("SERVICE_NAME", nameOfServiceFrom( protoLines, mainProtoFile ));
            hbCxt.data("ORG_NAME", nameOfOrganizationFromPackageName( protoLines, mainProtoFile ));
            File scriptFile = new File(outputDirectory, scriptName);
            if (!outputDirectory.exists()) outputDirectory.mkdirs();

            renderTemplateToFile(scriptTemplateLocation, hbCxt, scriptFile);
            scriptFile.setExecutable(true);

            File npmOutDir = new File(npmOut);
            if(!npmOutDir.exists()) npmOutDir.mkdirs();
            File packageFile = new File( npmOutDir, "package.json");
            renderTemplateToFile( packageJsonTemplateLocation, hbCxt, packageFile);

            File infoFile = new File( outputDirectory, "target/info.sh");
            renderTemplateToFile( infoTemplateLocation, hbCxt, infoFile);

            //go templating
            File goToolsDir = new File(outputDirectory, "target/proxy_src/tools");
            if( !goToolsDir.exists()){
                goToolsDir.mkdirs();
            }
            File goMain = new File( outputDirectory,"target/proxy_src/main.go");
            renderTemplateToFile( goMainTemplateLocation, hbCxt, goMain);
            File goMod = new File( outputDirectory,"target/proxy_src/go.mod");
            renderTemplateToFile( goModTemplateLocation, hbCxt, goMod);
            File goTools = new File( outputDirectory,"target/proxy_src/tools/tools.go");
            renderTemplateToFile( goToolsTemplateLocation, hbCxt, goTools);

            File goBuildFile = new File(outputDirectory, "go-build.sh");

            renderTemplateToFile(goBuildTemplateLocation, hbCxt, goBuildFile);
            goBuildFile.setExecutable(true);

        } catch (IOException e) {
            throw new MojoExecutionException(" Cannot render script", e);
        }

    }

    private String nameOfServiceFrom( List<String> protoLines, File protoFile) throws IOException {
        Pattern r = Pattern.compile(".*service +([a-zA-Z0-9_]+).*");
        for (String protoLine : protoLines) {
            Matcher matcher = r.matcher( protoLine);
            if( matcher.find()){
                return matcher.group(1);
            }
        }
        return "<ServiceNameWasNotFoundIn "+ protoFile.getAbsolutePath() +">";
    }

    private String nameOfOrganizationFromPackageName(List<String> protoLines, File protoFile) throws IOException {
        Pattern r = Pattern.compile(".*package +([a-zA-Z0-9_.]+).*");
        for (String protoLine : protoLines) {
            Matcher matcher = r.matcher( protoLine);
            if( matcher.find()){
                String fullPackageName = matcher.group(1);
                int lastDotIndex = fullPackageName.lastIndexOf('.');
                String orgName = fullPackageName.substring(0,lastDotIndex);
                return orgName;
            }
        }
        return "<PackageWasNotFoundIn "+ protoFile.getAbsolutePath() +">";
    }

    protected String nameOfFirstProto(List<String> protos){
        if( protos.size() == 0 ) return  "no-protos-provided";
        File f = new File(protos.get(0));
        String fileName = f.getName();
        String firstProtoName = fileName.substring(0, fileName.lastIndexOf('.'));
        return firstProtoName;
    }

    public void renderTemplateToFile(String templateLocation, Context handlebarContext, File outFile) throws IOException {
        System.out.println("outFile = " + outFile.getAbsolutePath());
        String templateText = getTemplate(templateLocation);
        OutputStream outputStream = new FileOutputStream(outFile);

        Handlebars handlebars = new Handlebars();

        Template template = handlebars.compileInline(templateText);

        String renderedTemplate = template.apply(handlebarContext);
        outputStream.write(renderedTemplate.getBytes());
        outputStream.close();
    }

    private String getTemplate(String scriptTemplateLocation) throws IOException {

        if (scriptTemplateLocation.startsWith(classpathPrefix)) {
            String resource = scriptTemplateLocation.substring(classpathPrefix.length() + 1);
            System.out.println("Reading template from resource:" + resource);
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(resource);
            return IOUtil.toString(is);
        } else {
            File inFile = new File(scriptTemplateLocation.substring(filePrefix.length()));
            System.out.println("Reading template from file:" + inFile.getAbsolutePath());
            return IOUtil.toString(new FileInputStream(inFile));
        }

    }
}
