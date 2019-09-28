package stu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stu.service.TestService01;

/**
 * @Author 马鹏勇
 * @Date 2019/9/28 下午2:24
 */
@Configuration
public class AppConfig {

	@Bean
	public TestService01 testService01(){
		return new TestService01();
	}
}
