package com.rabbit.pan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Rabbit 网盘后端启动类
 *
 * @author rabbit
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan("com.rabbit.pan.config")
@MapperScan("com.rabbit.pan.mapper")
public class RabbitPanApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitPanApplication.class, args);
    }
}
