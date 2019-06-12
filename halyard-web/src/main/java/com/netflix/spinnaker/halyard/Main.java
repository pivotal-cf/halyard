/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(
    value = {
      "com.netflix.spinnaker.halyard",
    })
@EnableAutoConfiguration
@EnableConfigServer
public class Main extends SpringBootServletInitializer {
  private static final Map<String, Object> DEFAULT_PROPS = buildDefaults();
  private static final Map<String, String> BOOTSTRAP_SYSTEM_PROPS = buildBootstrapProperties();

  private static Map<String, Object> buildDefaults() {
    Map<String, String> defaults = new HashMap<>();
    defaults.put("netflix.environment", "test");
    defaults.put("netflix.account", "${netflix.environment}");
    defaults.put("netflix.stack", "test");
    defaults.put("spring.config.node", "${user.home}/.spinnaker/");
    defaults.put("spring.config.name", "${spring.application.name}");
    defaults.put("spring.profiles.active", "${netflix.environment},local");
    // add the Spring Cloud Config "composite" profile to default to a configuration
    // source that won't prevent app startup if custom configuration is not provided
    defaults.put("spring.profiles.include", "composite");
    return Collections.unmodifiableMap(defaults);
  }

  private static Map<String, String> buildBootstrapProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("spring.application.name", "halyard");
    // default locations must be included pending the resolution
    // of https://github.com/spring-cloud/spring-cloud-commons/issues/466
    properties.put(
        "spring.cloud.bootstrap.location",
        "classpath:/,classpath:/config/,file:./,file:./config/,${user.home}/.spinnaker/");
    properties.put(
        "spring.cloud.bootstrap.name", "spinnakerconfig,${spring.application.name}config");
    properties.put("spring.cloud.config.server.bootstrap", "true");
    return properties;
  }

  public static void main(String... args) {
    BOOTSTRAP_SYSTEM_PROPS.forEach(
        (key, value) -> {
          if (System.getProperty(key) == null) {
            System.setProperty(key, value);
          }
        });

    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Main.class).run(args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.properties(DEFAULT_PROPS).sources(Main.class);
  }
}
