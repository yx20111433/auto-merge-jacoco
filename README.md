# auto-merge-jacoco
eclemma.org 的jacoco & eclemma的是一款使用JAVA AGENT及ASM技术进行测试覆盖率的工具，sysdeo 的tomcat插件是在ECLIPSE下运行tomcat的插件，相对ECLIPSE本身的SERVER 模式更加简单方便。 

Auto Merge Jacoco &tomcat 是对 eclemma.org 的jacoco & eclemma,以及sysdeo 的tomcat插件进行修改， 以适应大型项目测试需求的版本。 主要变化包括： 

1、 只分析运行过的class，而不是对整个project进行分析 2、 自动跟踪文件变化及合并之前的运行结果 3、 修改tomcat插件的启动，自动运行jacoco Agent以收集运行覆盖信息 
