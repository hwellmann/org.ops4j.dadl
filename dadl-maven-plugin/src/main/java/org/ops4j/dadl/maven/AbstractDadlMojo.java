/*
 * Copyright 2015 OPS4J Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.dadl.maven;

import java.io.File;
import java.io.IOException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.ops4j.dadl.generator.JavaModelGenerator;
import org.ops4j.dadl.metamodel.gen.Model;
import org.ops4j.dadl.model.ValidatedModel;

/**
 * @author hwellmann
 *
 */
public abstract class AbstractDadlMojo extends AbstractMojo {

    @Parameter(required = true)
    protected File model;

    @Parameter(name = "package", required = true)
    private String packageName;

    @Parameter(readonly = true, defaultValue = "${project}")
    protected MavenProject project;

    /**
     * @throws MojoFailureException
     */
    protected void generateJavaSources() throws MojoFailureException {
        ValidatedModel validatedModel = buildValidatedModel();

        JavaModelGenerator generator = new JavaModelGenerator(validatedModel, packageName,
            getOutputDir().toPath());
        try {
            generator.generateJavaModel();
        }
        catch (IOException exc) {
            throw new MojoFailureException("error generating Java model", exc);
        }
    }

    /**
     * @return
     * @throws MojoFailureException
     */
    private ValidatedModel buildValidatedModel() throws MojoFailureException {
        Model jaxbModel = buildJaxbModel();
        ValidatedModel validatedModel = new ValidatedModel(jaxbModel);
        validatedModel.validate();
        return validatedModel;
    }

    /**
     * @return
     * @throws MojoFailureException
     * @throws JAXBException
     */
    private Model buildJaxbModel() throws MojoFailureException {
        try {
            JAXBContext context = JAXBContext.newInstance(Model.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (Model) unmarshaller.unmarshal(model);
        }
        catch (JAXBException exc) {
            throw new MojoFailureException("error parsing DADL model", exc);
        }
    }

    public abstract File getOutputDir();

    /**
     * Gets the package name for the generated sources.
     *
     * @return the package name
     */
    public String getPackage() {
        return packageName;
    }

    /**
     * Gets the package name for the generated sources.
     *
     * @param packageName
     *            the genPackage to set
     */
    public void setPackage(String packageName) {
        this.packageName = packageName;
    }
}
