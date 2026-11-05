package com.ldd.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LddAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LddAgentApplication.class, args);
    }

}
