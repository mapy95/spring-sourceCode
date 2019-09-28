package stu.test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import stu.config.AppConfig;
import stu.service.TestService01;
import stu.service.TestService02;

/**
 * @Author 马鹏勇
 * @Date 2019/9/28 下午2:35
 */
public class CglibTest {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(AppConfig.class);
		System.out.println(ac.getBean(TestService01.class));
		System.out.println(ac.getBean(TestService02.class));
	}
}
