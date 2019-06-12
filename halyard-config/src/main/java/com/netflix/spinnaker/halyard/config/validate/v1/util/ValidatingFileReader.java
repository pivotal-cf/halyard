/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.halyard.config.validate.v1.util;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.NoSuchResourceException;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.cloud.config.server.support.EnvironmentPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class ValidatingFileReader {

  @Autowired private ResourceRepository resourceRepository;
  @Autowired private EnvironmentRepository environmentRepository;

  @Value("${spring.application.name")
  private String applicationName;

  private static final String NO_ACCESS_REMEDIATION =
      "Halyard is running as user "
          + System.getProperty("user.name")
          + ". Make sure that user can read the requested file.";

  public String contents(ConfigProblemSetBuilder ps, String path) {
    if (path.startsWith("configserver:")) {
      return retrieveFromConfigServer(ps, path);
    } else {
      return readFromLocalFilesystem(ps, path);
    }
  }

  private String readFromLocalFilesystem(ConfigProblemSetBuilder ps, String path) {
    try {
      return IOUtils.toString(new FileInputStream(path));
    } catch (FileNotFoundException e) {
      buildProblem(ps, "Cannot find provided path: " + e.getMessage() + ".", e);
    } catch (IOException e) {
      buildProblem(ps, "Failed to read path \"" + path + "\".", e);
    }

    return null;
  }

  private String retrieveFromConfigServer(ConfigProblemSetBuilder ps, String path) {
    try {
      String fileName = path.substring("configserver:".length());
      Resource resource = this.resourceRepository.findOne(applicationName, null, null, fileName);
      try (InputStream is = resource.getInputStream()) {
        Environment environment = this.environmentRepository.findOne(applicationName, null, null);

        String text = StreamUtils.copyToString(is, Charset.forName("UTF-8"));
        text =
            EnvironmentPropertySource.resolvePlaceholders(
                EnvironmentPropertySource.prepareEnvironment(environment), text);
        return text;
      }
    } catch (NoSuchResourceException e) {
      buildProblem(ps, "The resource \"" + path + "\" was not found in config server.", e);
    } catch (IOException e) {
      buildProblem(ps, "Failed to retrieve resource \"" + path + "\" from config Server.", e);
    }

    return null;
  }

  private void buildProblem(ConfigProblemSetBuilder ps, String message, Exception exception) {
    ConfigProblemBuilder problemBuilder =
        ps.addProblem(Problem.Severity.FATAL, message + ": " + exception.getMessage() + ".");

    if (exception.getMessage().contains("denied")) {
      problemBuilder.setRemediation(ValidatingFileReader.NO_ACCESS_REMEDIATION);
    }
  }
}
