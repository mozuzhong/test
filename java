
故障及调优



常用工具
这些工具都是java官方提供的，其实他们仅仅是tool.jar包的包装而已，运行java -cp lib/tools.jar sun.tools.jps.Jps和jps命令是等价的。
Jstatd
即虚拟机的jstat守护进程，主要用于支持jstat工具的远程代理，但是目前像jps也是支持的。jstatd启动一个接口并允许远程监控工具依附到在本地主机上运行的JVM。它的作用相当于代理服务器，建立本地计算机与远程监控工具的通信。jstatd服务器将本级的Java应用程序信息传递到远程计算机。
jstatd在远程服务端启动一个RMI server，允许本地通过jps、jstat命令行工具及jvisualvm可视化工具监控远程服务器的Java进程。其本身也是个java进程。

（1）安全策略
因为担心外部网络攻击，默认是不具有访问权限的。所以必须为jstatd指定安全策略，因为一旦启动之后，就是裸奔状态了。
所以启动时首先要为其编写一个策略文件，写在哪都行，策略可以开放全部权限，如果不配会出现could not create remote object错误。
grant codebase "file:/usr/java/jdk/lib/tools.jar" {
    permission java.security.AllPermission;
};
（2）与rmi的关系
jstatd -J-Djava.security.policy=jstatd.all.policy这个示例使用默认端口1099。
因为没有指定rmi register，所以会尝试用默认端口连接本机，找不到且没有-nr属性，那么就自己启动rmi register 端口监听。
注意如果是启动jstatd内部的注册中心，那么并不是通过java目录下的rmiregistry 工具起的，而是直接在jstatd内部开tcp线程监听，这点可以通过查看端口验证，此端口的开启者正是jstatd。

如果要使用外部的rmi服务，一般是使用rmiregistry工具启动：
rmiregistry 2020
jstatd -J-Djava.security.policy=all.policy -p 2020

（3）参数
-p port 在指定的端口（从这也看出jstatd的rmi register必须和jstatd进程在一台机子上，因为没别的参数指定rmi registerIP了）查找RMI注册表服务。如果没有找到，并且没有指定-nr选项，则使用该端口自行创建一个内部的RMI注册表。
-nr 当找不到现有的RMI注册表时，不尝试使用jstatd进程创建一个内部的RMI注册表。
可以用这个参数验证一下，独立的rmi register是不是真实有效的。
-n rminame RMI注册表中绑定的RMI远程对象的名称。默认的名称为JStatRemoteHost。如果多个jstatd服务器在同一主机上运行，你可以通过指定该选项来让每个服务器导出的RMI对象具有唯一的名称。此时监控客户端的hostid和vmid连接信息要包含这个名称。
与任何Java选项一起使用，可将option以下内容-J（-J和选项之间没有空格）传递给Java解释器。
-J-Djava.rmi.server.logCalls开启调用日志
-J-Djava.security.policy=策略文件路径
-J-Djava.rmi.server.hostname 
这个是注册到RMI注册中心的IP地址，以后客户端请求时，从RMI注册中心拿到的就是这个IP地址，然后使用这个IP地址和jstatd建立联系，所以一定要填对。有时候这个参数没填对，jstatd一样是会起来不报错的，但是客户端连接时拿到的是错误的Ip，无法正常工作。

（4）启动命令
jstatd -J-Djava.security.policy=策略文件路径 -J-Djava.rmi.server.logCalls=true -J-Djava.rmi.server.hostname=192.168.1.14
其中
-J-Djava.rmi.server.logCalls开启调用日志
-J-Djava.rmi.server.hostname 

启动之后，jps（jps -l rmi://rmi register 的IP:20000）、jstat等工具就可以连接了。

（5）总结
jstatd的优势是目标进程启动不用加额外的参数，当想监视运行环境问题时，才需要使用该工具
因为jstatd有可能碰到权限的问题，出现不受此jvm支持，目前还没找到解决方案，改用jmx就可以。
一般我们更应该偏重使用jmx，碰到的问题更少。



jps

参数
-q	只显示本地虚拟机标识符lvmid(local JVM identifiers)，注意进程号是和ps命令一样的
-m	显示传递给main方法的参数
-l	显示应用程序主类的包名或者jar的全路径
-v	显示传递给虚拟机的启动参数，对于优化来说很重要
-V	官方文档介绍与-q参数功能一致，实测没卵用，可能是我打开方式不对，记得留言我
hostid	当目标机器是远程机器时使用，用于标识远程机器
 hostid是需要写明协议、地址、端口和注册在rmi register 的jstatd服务名称的URL：
 jps -lmv  rmi://192.168.1.8:9001/JStatRemoteHost
 如果jstatd启动时没有特别指定-n选项，则JStatRemoteHost可以省略：
 jps -lmv  rmi://192.168.1.8:9001

输出参数格式：
lvmid [ [ classname | JARfilename | “入口类名”] [ arg* ] [ jvmarg* ] ]


jinfo
查看jvm扩展启动参数（-XX开头），因为有些参数是默认的，可以通过这个指令动态查看、设置参数。


jmap
查看jvm中类的各个实例数量统计，也就是堆快照。
当然最大的作用就是导出堆快照，供其他工具分析：
jmap -dump:format=b,file=./dump.hprof  系统进程号

jstat

是监视java类加载器、堆使用情况、GC情况、jit编译器情况等运行信息的工具


jstack
是分析线程堆栈的利器。

使用方法：
jstack  选项 进程/线程IP
jstack  选项 core文件
jstack  选项 远程服务

参数说明：
-F	当正常输出的请求不被响应时，强制输出线程堆栈
-m	如果调用到本地方法的话，可以显示C/C++的堆栈
-l	除堆栈外，显示关于锁的附加信息，在发生死锁时可以用jstack -l pid来观察锁持有情况

如果只想看某个线程，那么使用top -Hp pid 查看该进程下所有线程，然后jstack 此线程ID得到此线程的堆栈信息。那么问题来了？top命令下展示的进程IP是主进程还是什么？jstack到底接受线程ID还是进程ID？


jcmd
jdk7之后，用这个工具替代了上面的所有命令行工具。


jconsole
多合一，同时支持插件扩展，插件就是增加某个标签页面。jconsole其实能做到的就是轻量级监控，并不能深入分析。
线程、内存、类、cpu都可以选择不同的图形周期
线程标签的死锁检测可以检测到死锁
内存标签的执行gc，强制gc一次，也很有用
MBean的操作
这个功能倒是非常强大。


最后强调一下，插件才是我们关注的核心，常用的强大插件有：





visualvm

已经作为jdk工具之一，也可以单独安装使用。


一些遗留问题：
一个比较重要的问题是工具现在由orical维护，但是市面上又有很多openjdk部署在服务器上，因此可能出现无法支持的情况。
有个问题一直没解决，就是使用jstatd启动远程代理之后，visualvm连接上去之后，cpu、线程总是提示jvm不支持，其他监控倒是正常，不知道哪里配的不对，目前jmx方式连接是没问题的。


jhat
通过输入堆栈文件，分析生成分析结果，并通过http服务展示，通过链接一步步进行索引。
当然最强大的还是OQL语言查询功能。




Jmx

JMX的全称为Java Management Extensions,是管理Java的一种扩展。这种机制可以方便的管理正在运行中的Java程序，个人的理解是JMX让程序有被管理的功能，其中MBean是MBean是Management Bean的缩写。常用于管理线程，内存，日志Level，服务重启，系统环境等。
JMX是一份规范，SUN依据这个规范在JDK（1.3、1.4、5.0）提供了JMX接口。而根据这个接口的实现则有很多种，比如Weblogic的JMX实现、MX4J、JBoss的JMX实现，JConsole和JVisualVM中能够监控到JAVA应用程序和JVM的相关信息都是通过JMX实现的。
（1）基本原理
jmx是分层架构的

最下面的是基础层，是被管理的MBean。一共有四种类型的 MBean ， 分别是标准类型 MBean（StdMBean接口描述行为，Std类实现行为； 要求：①接口和类必须在一个包下 ②接口名MBean结尾，实现类名为接口名前半部分）， 动态类型 MBean， 开放类型 MBean 和模型类型 MBean。一般用标准型就行，其他是一些特殊场景下用的。
中间是适配层MBeanServer，即存放MBean组件的容器，提供注册和请求MBean的功能，容器就是管理组件服务器 / MBeanServer，创建管理组件服务器时需要制定服务器的域名，当向管理组件服务器注册管理组件时需要指定一个ObjectName对象来标识管理组件，ObjectName对象由域名和键值对列表构成，域名就是创建管理组件服务器的域名，键值对用来区分不同的MBean至少有一对且一般有name=XXX;
最上面是接入层，管理客户端如何访问管理组件服务器中的管理组件，有协议适配器或连接器两种实现形式，而适配器和连接器也作为一种MBean注册到管理组件服务器，常见的就是HTML适配器和RMI连接器，HTML适配器可以用浏览器客户端访问，RMI连接器可以用JConsole等客户端访问。最常用的JMX适配器了，我们重点研究这个，当然像HtmlAdaptorServer这样的html也很好。

总体看jmx和业务对象的关系如下图所示，业务对象对于jmx是无感知的，有没有jmx业务对象也都只是按照自己的规则服务。jmx可能仅仅是读业务对象属性，也可能直接修改其行为。



（2）与RMI的关系
我们知道jmx对外提供的适配器是可以有很多种的，rmi是其中一种，也是默认的连接器，我们重点研究的也是这种模式。
如果选择用rmi来开放接口，那么JMX就基于RMI，首先也需要使用RMI注册中心，然后jmx会启动一个监听端口，客户端和服务端就是通过这个端口建立连接的。在实现时使用RMI的API来达到访问远程对象的目的。

（3）URL格式
service:jmx:rmi://hostname1:port1/jndi/rmi://hostname2:port2/jmxrmi
对于服务端和客户端来说格式一样，但是含义却不一样，对于服务端来说：
service:jmx:rmi
表示JMX服务使用JRMP协议进行通讯，JRMP协议是RMI通讯的标准协议。当然还有其他模式，我们不重点研究。
hostname1
这个host原本设计的作用是标记绑定哪个IP地址。但是如果是采用和RMI服务协作的方式，那么这个host可能就没用了（正常来说使用rmi时，注册时也应该用这个IP读取注册的，但很多实现并没有这么做）。
那么服务器向RMI注册时后，客户端从RMI拿到的服务器IP到底在哪设置的？这个就要看具体的工具实现了，如果客户端拿到了一个怪异的IP或者无法访问的IP，极有可能就是这个原因。比如通过java参数启动jmx服务时，参数里面可以指定java.rmi.server.hostname作为向RMI注册时的IP，客户端拿到的就是这个IP。特别注意这个参数并不是指rmi服务地址。
像java官方提供的APIjavax.management.remote.JMXConnectorServer，内部也是并没有使用这里给出的host，而是直接使用了本地IP；
port1
这是JMX server提供的用于通讯的端口，如果不写，JMX server会随机分配一个；这个端口会在JMX client访问RMI Registry后返回给JMX client，JMX client拿到这个端口就会使用此端口与JMX server正式通讯了
jndi
使用jndi服务对JMX服务进行注册，这里不深究这个问题了。
rmi://hostname2:port2
说明要注册到RMI Registry上，RMI Registry的访问地址为rmi://hostname2:port2。
jmxrmi
当用同一个注册中心管理多个服务时，服务名就很重要了；
RMI Registry中注册的服务名叫jmxrmi；
jmxrmi也是JMX服务的默认注册名；
用户可以设置自己的注册名

对于客户端来说：
hostname1:port1
如果是通过rmi协作的，那么这两项可以不填。因为要访问的JMX服务的hostname和port都是RMI Registry返回的。
rmi://hostname2:port2/jmxrmi
jndi服务内部是到RMI Registry上去查找服务的，RMI Registry的访问地址是hostname2:port2，查找服务的注册名为jmxrmi；当查找成功后，返回信息中会包含JMX服务对外的hostname和port，JMX client使用这个hostname和port就可以和JMX server上的JMX服务通讯。
（4）名字规则
对于MBean，其命名是有规则的：域:键值对列表（逗号分隔），如com.sun.someapp:type=Whatsit,name=25
支持*、?等通配符

（5）通过java启动参数启动代理服务端
因为java默认自带的了JMX RMI的连接器。所以，只需要在启动java程序的时候带上运行参数，就可以开启RMI协议的连接器。
java -Dcom.sun.management.jmxremote -Djava.rmi.server.hostname=10.227.105.79 -Dcom.sun.management.jmxremote.port=20000 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false  -classpath kkkk-1.0-SNAPSHOT.jar Main
参数：
-Dcom.sun.management.jmxremote 表示启动jmx
-Djava.rmi.server.hostname 表示注册到rmi注册中心时，注册的IP地址
-Dcom.sun.management.jmxremote.port=3000  指定rmi服务端口
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false 

认证与授权使用在安全要求比较高的生产环境。

（6）代码实现代理端和客户端
本地管理
如果说Agent只是被本地监控工具使用，比如本地的JConsole，只需要通过MBeanServer注册MBean即可。不需要配置协议适配器，java本身有一套本地的访问机制。
public static void main(String[] args) throws JMException, Exception
     {
          MBeanServer server = ManagementFactory.getPlatformMBeanServer();
          ObjectName helloName = new ObjectName("jmxBean:name=hello");
          //create mbean and register mbean
          server.registerMBean(new Hello(), helloName);
          Thread.sleep(60*60*1000);
     }

那么这些工具是怎么管理本地java进程的？
当我们启动java进程后，经常会使用jps，jinfo，jmap，jstat等jdk自带的命令去查询进程的状态，这其中的原理就是，当java进程启动后，会创建一个用于本机连接的“localConnectorAddress”放到当前用户目录下，当使用jps等连接时，会到当前用户目录下取到“localConnectorAddress”并连接。可以参考这篇文章的实现https://www.bbsmax.com/A/LPdoBG3253/


  支持远程管理
   public static void remotetest() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName helloName = null;
        try {
            helloName = new ObjectName("jmxBean:name=hello");
            server.registerMBean(new Hello(), helloName);

            //这个步骤很重要，注册一个端口，绑定url后用于客户端通过rmi方式连接JMXConnectorServer
//            LocateRegistry.createRegistry(20000); //启动内部的rmi注册中心
            //URL路径的结尾可以随意指定，但如果需要用Jconsole来进行连接，则必须使用jmxrmi
            JMXServiceURL url = new JMXServiceURL
                    ("service:jmx:rmi://localhost:20001/jndi/rmi://localhost:20000/jmxrmi");
            JMXConnectorServer jcs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, server);
            System.out.println("begin rmi start");
            jcs.start();
            System.out.println("rmi start");
        } catch (Exception e) {
            e.printStackTrace();
        }
     }

远程连接客户端可以是jconsol、代码实现等，从代码中我们可以看出成员属性设置、方法调用的几种方式。

 public static void main(String[] args) throws IOException, Exception, NullPointerException
    {
        JMXServiceURL url = new JMXServiceURL
                ("service:jmx:rmi:///jndi/rmi://localhost:20000/jmxrmi");
        JMXConnector jmxc = JMXConnectorFactory.connect(url,null);
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
        //ObjectName的名称与前面注册时候的保持一致
        ObjectName mbeanName = new ObjectName("jmxBean:name=hello");
        System.out.println("Domains ......");
        String[] domains = mbsc.getDomains();

        for(int i=0;i<domains.length;i++)
        {
            System.out.println("doumain[" + i + "]=" + domains[i] ); 
        }

        System.out.println("MBean count = " + mbsc.getMBeanCount());
        //设置指定Mbean的特定属性值
        //这里的setAttribute、getAttribute操作只能针对bean的属性
        //例如对getName或者setName进行操作，只能使用Name，需要去除方法的前缀
        mbsc.setAttribute(mbeanName, new Attribute("Name","杭州"));
        mbsc.setAttribute(mbeanName, new Attribute("Age","1990"));
        String age = (String)mbsc.getAttribute(mbeanName, "Age");
        String name = (String)mbsc.getAttribute(mbeanName, "Name");
        System.out.println("age=" + age + ";name=" + name);

        HelloMBean proxy = MBeanServerInvocationHandler.
                newProxyInstance(mbsc, mbeanName, HelloMBean.class, false);
        proxy.helloWorld();
        proxy.helloWorld("migu");
        proxy.getTelephone();
        //invoke调用bean的方法，只针对非设置属性的方法
        //例如invoke不能对getName方法进行调用
        mbsc.invoke(mbeanName, "getTelephone", null, null);
        mbsc.invoke(mbeanName, "helloWorld",
                new String[]{"I'll connect to JMX Server via client2"}, new String[]{"java.lang.String"});
        mbsc.invoke(mbeanName, "helloWorld", null, null);
    }
	
通知机制
MBean之间的通信机制。共有4个角色：
1、Notification这个相当于一个信息包，封装了需要传递的信息
2、Notification broadcaster这个相当于一个广播器，把消息广播出。
3、Notification listener 这是一个监听器，用于监听广播出来的通知信息。
4、Notification filiter 这个一个过滤器，过滤掉不需要的通知。这个一般很少使用。

先建立两个MBean
public interface HelloMBean
{
    public String getName();   
 
    public void setName(String name);   
 
    public void printHello();   
 
    public void printHello(String whoName);
}
public class Hello implements HelloMBean
{
    private String name;
 
    public String getName()
    {
        return name;
    }
 
    public void setName(String name)
    {
        this.name = name;
    }
 
    public void printHello()
    {
        System.out.println("Hello World, " + name);
    }
 
    public void printHello(String whoName)
    {
        System.out.println("Hello , " + whoName);
    }
}

public interface JackMBean
{
    public void hi();
}

public class Jack extends NotificationBroadcasterSupport implements JackMBean
{
    private int seq = 0;
    public void hi()
    {
         //创建一个信息包
        Notification notify =
            //通知名称；谁发起的通知；序列号；发起通知时间；发送的消息
            new Notification("jack.hi",this,++seq,System.currentTimeMillis(),"jack");
        sendNotification(notify);
    }
 
}

再定义一个监听类
public class HelloListener implements NotificationListener
{
 
    public void handleNotification(Notification notification, Object handback)
    {
        if(handback instanceof Hello)
        {
            Hello hello = (Hello)handback;
            hello.printHello(notification.getMessage());
        }
    }
 
}

启动测试
public static void main(String[] args) throws JMException, Exception
    {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName helloName = new ObjectName("yunge:name=Hello");
        Hello hello=new Hello();
        server.registerMBean(hello, helloName);
        Jack jack = new Jack();
        server.registerMBean(jack, new ObjectName("jack:name=Jack"));
        //添加监听器
        jack.addNotificationListener(new HelloListener(), null, hello);
        Thread.sleep(500000);
    }

整个消息工作机制就是发送方通过addNotificationListener(NotificationListener listener, NotificationFilter filter,Object handback)添加监听者，每调用一次就会向监听队列中加入一个监听对象。
发送消息是发送方覆盖sendNotification实现的，要先构造一个Notification对象（内容包括名称、消息发出的对象、序号、时间、message），然后调用。
消息从发送方发出之后，会遍历监听者列表，并调用其handleNotification(Notification notification, Object handback)方法，其中handback就是addNotificationListener指定的那个对象，这样每个监听者都能够根据需要处理自己的消息。

java进程自带的mbean
当我们在用jconsole、jvisualvm进行监控java进程时，通常都能看到cpu、内存、线程、垃圾收集等使用情况，其实数据都是通过jmx从jvm提供的一些mbean里面取的。主要如下：
ClassLoadingMXBean、CompilationMXBean、GarbageCollectorMXBean、MemoryManagerMXBean、MemoryMXBean、MemoryPoolMXBean、OperatingSystemMXBean、RuntimeMXBean、ThreadMXBean
如何获取他们呢？以类加载器为例：
ClassLoadingMXBean mbs = ManagementFactory.getClassLoadingMXBean();
System.out.println("loadedClass:" + mbs.getLoadedClassCount()); 

（7）代码和命令行参数启动方式的关系

如果在代码里面写了jmx服务相关的内容，然后java命令行启动又加了参数，会不会打架？
还真会打架，但是也能够完美避开。怎么做到的？
其实不管是命令行启动，还是代码里面启动，底层使用的都是一套，并且jvm只有一个，对应的MBean服务一般也只会有一个，区别仅仅是从不同端口请求进来而已。因此即使用两种方式启动，只要避开端口就都是允许的，不过他们都访问的一个MBean，所以最好不要用两种方式启动，这样会多占用端口，因此具体要采用哪种方式，要看具体的业务需要。

这里有个地方主要一下，用代码实现jmx时，其URLjavax.management.remote.JMXServiceURL（service:jmx:rmi://10.10.10.10:20001/jndi/rmi://localhost:20000/jmxrmi）第一个IP用不上，源码里面确实没用到，所以如果要达到指定IP注册到注册中心，那么要用更底层的RMI API，目前看JMX实现是没有做到的，JMX注册的IP都是本地IP。虽然url格式中的第一个IP没用上，但是端口是有用的。后续客户端从注册中心拿到的端口就是这个指定的端口。
而如果启动java进程却可以指定IP，不知道怎么做到的，遗憾的是却又不可以指定端口，他两就是一对互补啊。目前没时间深入研究源码实现。


（8）应用

1、用来管理应用程序的配置项，可以在运行期动态改变配置项的值，而不用妨碍程序的运行，这对与许多可靠性要求较高的应用来说非常方便。

可以通过jconsole等JMX客户端工具动态改变配置项的值。

2、用来对应用程序的运行进行监控，比如对一个大型交易处理程序，我们要监控当前有多少交易在排队中，每笔交易的处理时间是多少，平均每处理一笔交易要花多少时间等等。

中间件软件WebLogic的管理页面就是基于JMX开发的，而JBoss则整个系统都基于JMX构架。

3、动态修改线上日志级别
以logback为例，只需在logback.xml配置文件中，增加<jmxConfigurator />单行配置即可启动JMX支持。[logback官方示例]

4、查看quartz-job任务执行情况

以spring-boot为例，可以在application.yml配置文件中增加相关配置

5、通知告警
主要利用notify机制，可以实现发短信、邮件等。

6、spring中使用

必须打开开关spring.jmx.enabled=true
然后使用注解  ManagedResource\ManagedAttribute\ManagedOperation实现。

rmi（Java Remote Method Invocation）
是让客户端对远程方法的调用可以相当于对本地方法的调用而屏蔽其中关于远程通信的内容，即使在远程上，也和在本地上是一样的。调用可以跨主机，这本来是java社区用来解决java本身服务调用的解决方案，相比rpc来说更为先进。
后来rmi应用到了远程监控等领域，借助rmi的机制，实现远程监控就简单的多，如jmx、jstatd等工具。

（1）角色
总共存在三个角色
第一个是rmiregistry

注册表程序时运行在一个单独的进程中的，它作为一个第三方的组件，来协调客户端和服务器之间的通信，但是与它们两个之间是完全解决解耦的。默认监听1099端口的请求。
JDK提供的一个可以独立运行的程序，在bin目录下的rmiregister工具可以起一个注册中心，不过这种方式起的服务，有时候会报找不到类的情况，此时把要注册的类型的路径加入classpath即可。
当然通过类java.rmi.registry.LocateRegistry也可以完成同样的目的。
第二个是server端的程序，对外提供远程对象

一般在server内部直接通过LocateRegistry起一个注册服务，但也有使用另外进程的注册中心的。
第三个是client端的程序，想要调用远程对象的方法。




这个模型与当前流行的微服务架构如出一辙。
RMI概念	RMI实现	     微服务概念       	实现
RMI server	jstatd	                    生产者	         服务提供方
RMI client	jps、jstat、jvisualvm	消费者	      服务调用方
RMI registry	rmiregistry	      注册中心	     zk 、eureka、nacos

（2）工作原理


光看图或者文字描述不够准确，show me the code。

远程对象的接口定义-指明远程对象对外提供的服务

public class User implements Serializable {//必须可序列化，否则网络传输出问题。
    // 服务端 客户端的serialVersionUID字段数据要保持一致
    private static final long serialVersionUID = 1L;
    private String name;
    private int age;
}
public interface IUpdateUser extends Remote {//Remote也是可序列化的。
    User updateUser(User u) throws RemoteException;
}

远程对象实现
/**
 * 只有继承UnicastRemoteObject类，才表明其可以作为远程对象，被注册到注册表中供客户端远程调用
 * （补充：客户端lookup找到的对象，只是该远程对象的Stub（存根对象），
 * 而服务端的对象有一个对应的骨架Skeleton（用于接收客户端stub的请求，以及调用真实的对象）对应，
 * Stub是远程对象的客户端代理，Skeleton是远程对象的服务端代理，
 * 他们之间协作完成客户端与服务器之间的方法调用时的通信。）
 */
public class UpdateUserImpl extends UnicastRemoteObject implements IUpdateUser {
    private static final long serialVersionUID = 1L;
	public User updateUser(User u) throws RemoteException {
		真正业务逻辑...
	}
}

服务端代码

public class Server {
    public static void main(String[] args) throws Exception {
        try{
            // 本地主机上的远程对象注册表Registry的实例,并指定端口为8888，这一步必不可少（Java默认端口是1099）。当然也可以使用外部的rmi注册中心。
            LocateRegistry.createRegistry(8888);
            //创建一个远程对象
            IUpdateUser rUpdateUser=new UpdateUserImpl();
            //把远程对象注册到RMI注册服务器上,命名为 rUpdate
            //绑定的URL标准格式为：rmi://host:port/name(其中协议名可以省略，下面两种写法都是正确的）
            // Naming.bind("//localhost:8888/rUpdate",rUpdateUser);
            Naming.bind("rmi://localhost:8888/rUpdate",rUpdateUser);
            System.out.println("------------远程对象IUpdateUser注册成功，等待客户端调用...");
        }catch (Exception e){
            System.out.println("发生异常！");
        }
    }
}

查看注册的内容（可选，有时候有bug的时候可以看看）
java代码的实现：
RMI服务是可以非本地的，通过下面的代码遍历登记的内容
String host = "10.227.105.83";
int port = 20000;
Registry registry = LocateRegistry.getRegistry(host, port);
for (String name : registry.list()) {
    System.out.println(name);
}
Remote r = registry.lookup("jmxrmi");//或者Remote r = Naming.lookup("//10.227.105.83:20000/jmxrmi");
System.out.println(r.toString());

客户端调用
public class Client {
    public static void main(String[] args) {
        try{
            // 在RMI服务注册表中查找名称为rUpdate的对象，并调用其上的方法
            IUpdateUser rUpdateUser = (IUpdateUser) Naming.lookup("rmi://localhost:8888/rUpdate");
            //构造User对象，测试远程对象传输
            User user = new User("甲粒子",20);
            System.out.println("-------------- 服务端返回的的user为" + rUpdateUser.updateUser(user).toString());
        } catch (Exception e) {
            e.printStackTrace();
        } 
    }
}

总结

RMI的本质就是实现在不同JVM之间的调用,它的实现方法就是在两个JVM中各开一个Stub和Skeleton，二者通过socket通信来实现参数和返回值的传递。

其实在服务端注册到注册中心的内容并不能把整个实例的信息都写进去，其注册的仅仅是类型信息+实例唯一ID，并不包括实例成员内容。有了类型信息和实例ID，就能够到jvm里面找到正确的对象。我们可以做个实验，当服务注册完成之后，停掉服务只保留注册服务运行，客户端仍旧能够顺利从注册中心拿到数据，但是调用的时候就报错了。

另外每次注册的时候，每个对象都是具有唯一标识的，这个唯一标识也记录在注册中心，如果服务器重启了，且没有重新绑定，那么客户端从注册中心拿到的是旧的对象，此时调用旧对象的方法是会报错的。此时服务端应该使用rebind重新绑定覆盖旧对象。

当调用方法时，传递的参数是要经过网络iO的，因此必须是可序列化的。

java.rmi.server.hostname这个参数是为指定服务器向注册中心注册时填入的IP，当客户端获取时，得到的就是这个IP。可以通过启动参数启动，也可以通过System.setProperty("java.rmi.server.hostname",IP)指定。

（3）一个手写的例子
定义接口类型

public interface Person {      
    public int getAge() throws Throwable;      
    public String getName() throws Throwable;      
}

stub实现
import java.io.ObjectOutputStream;      
import java.io.ObjectInputStream;      
import java.net.Socket;      
public class Person_Stub implements Person {      
    private Socket socket;      
    public Person_Stub() throws Throwable {      
        // connect to skeleton      
        socket = new Socket("computer_name", 9000);      
    }      
    public int getAge() throws Throwable {      
        // pass method name to skeleton      
        ObjectOutputStream outStream =      
            new ObjectOutputStream(socket.getOutputStream());      
        outStream.writeObject("age");      
        outStream.flush();      
        ObjectInputStream inStream =      
            new ObjectInputStream(socket.getInputStream());      
        return inStream.readInt();      
    }      
    public String getName() throws Throwable {      
        // pass method name to skeleton      
        ObjectOutputStream outStream =      
            new ObjectOutputStream(socket.getOutputStream());      
        outStream.writeObject("name");      
        outStream.flush();      
        ObjectInputStream inStream =      
            new ObjectInputStream(socket.getInputStream());      
        return (String)inStream.readObject();      
    }
} 


骨干的实现
import java.io.ObjectOutputStream;      
import java.io.ObjectInputStream;      
import java.net.Socket;      
import java.net.ServerSocket;      
public class Person_Skeleton extends Thread {      
    private PersonServer myServer;      
    public Person_Skeleton(PersonServer server) {      
        // get reference of object server      
        this.myServer = server;      
    }      
    public void run() {      
        try {      
            // new socket at port 9000      
            ServerSocket serverSocket = new ServerSocket(9000);      
            // accept stub's request      
            Socket socket = serverSocket.accept();      
            while (socket != null) {      
                // get stub's request      
                ObjectInputStream inStream =      
                    new ObjectInputStream(socket.getInputStream());      
                String method = (String)inStream.readObject();      
                // check method name      
                if (method.equals("age")) {      
                    // execute object server's business method      
                    int age = myServer.getAge();      
                    ObjectOutputStream outStream =      
                        new ObjectOutputStream(socket.getOutputStream());      
                    // return result to stub      
                    outStream.writeInt(age);      
                    outStream.flush();      
                }      
                if(method.equals("name")) {      
                    // execute object server's business method      
                    String name = myServer.getName();      
                    ObjectOutputStream outStream =      
                        new ObjectOutputStream(socket.getOutputStream());      
                    // return result to stub      
                    outStream.writeObject(name);      
                    outStream.flush();      
                }      
            }      
        } catch(Throwable t) {      
            t.printStackTrace();      
            System.exit(0);      
        }      
    }           
}

service实现
public class PersonServer implements Person {      
    private int age;      
    private String name;      
    public PersonServer(String name, int age) {      
        this.age = age;      
        this.name = name;      
    }      
    public int getAge() {      
        return age;      
    }      
    public String getName() {      
        return name;      
    }      
    
    public static void main(String args []) {      
        // new object server      
        PersonServer person = new PersonServer("Richard", 34);      
        Person_Skeleton skel = new Person_Skeleton(person);      
        skel.start();      
    } 
}
client实现
public class PersonClient {      
    public static void main(String [] args) {      
        try {      
            Person person = new Person_Stub();      
            int age = person.getAge();      
            String name = person.getName();      
            System.out.println(name + " is " + age + " years old");      
        } catch(Throwable t) {      
            t.printStackTrace();      
        }      
    }      
}      






















