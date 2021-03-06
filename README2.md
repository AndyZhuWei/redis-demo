#第二部分 单机数据库的实现
## 9.1 服务器中的数据库
Redis服务器将所有数据库都保存在服务器状态redisServer结构的db数组中，db数组的每个项都是一个redisDb结构
每个redisDb结构代表一个数据库
```
strcut redisServer{
    //...
    //一个数组，保存着服务器中的所有数据库
    redisDb *db;
    //服务器的数据库数量
    int dbnum;
    //...
};
```
在初始化服务器时，程序会根据服务器状态的dbnum属性来决定应该创建多少个数据库
dbnum属性的值由服务器配置的database选项决定，默认情况下为16

### 9.2 切换数据库
每个Redis客户端都有自己的目标数据库，每当客户端执行数据库写命令或者数据库读命令的时候，目标
数据库就会成为这些命令的操作对象。默认情况下，Redis客户端的目标数据库为0号数据库，但客户端可以
执行select命令来切换目标数据库

在服务器内部，客户端状态redisClient结构的db属性记录了客户端当前的目标数据库，这个属性是一个指向redisDb结构的指针：
```
typedef struct redisClient{
    //...
    //记录客户端当前正在使用的数据库
    redisDb *db;
    //...
}redisClient;
```
redisClient.db指针指向redisServer.db数组的其中一个元素，而被指向的元素就是客户端的目标数据库
通过修改redisClient.db指针，让它指向服务器中的不同数据库，从而实现切换目标数据库的功能——这就是select命令的实现原理
为了避免对数据库进行误操作，在执行Redis命令特别是像FLUSHDB这样的危险命令之前，最好先执行一个SELECT
命令，显示切换到指定的数据库，然后才执行别的命令

### 9.3 数据库键空间
Redis是一个键值对数据库服务器，服务器中的每个数据库都由一个redisDb结构表示，其中redisDb结构
dict字典保存了数据库中的所有键值对，我们将这个字典称为键空间
```
typedef struct redisDb {
    //...
    //数据库键空间，保存着数据库中的所有键值对
    dict *dict;
    //...
}redisDb;
```
   因为数据库的键空间是一个字典，所以所有针对数据库的操作，比如添加一个键值对到数据库，或者从数据库中删除一个键值对，
又或者在数据库中获取某个键值对等，实际上都是通过对键空间字典进行操作来实现的。

#### 9.3.6 读写键空间时的维护操作
当使用Redis命令对数据库进行读写时，服务器不仅会对键空间执行指定的读写操作，还会执行一些额外的维护操作其中包括：
1>在读取一个键之后（读操作和写操作都要对键进行读取），服务器会根据键是否存在来更新服务器的键空间命中次数和键空间不命中底数，
   这两个值可以在info stats命令的keyspace_hits属性和keyspace_misses属性中查看
2>在读取一个键之后，服务器会 更新键的LRU(最后一次使用)时间，这个值可以用于计算键的闲置时间,使用OBJECT idletime<key>命令可以查看键key的闲置时间
3>如果服务器在读取一个键时发现该键已经过期了，那么服务器会先删除这个过期键，然后才执行余下的其他操作.
4>如果有客户端使用WATCH命令监视了某个键，那么服务器在对被监视的键进行修改之后，会将这个键标记为肮，从而让事务程序注意到这个键已经被修改过
5>服务器每次修改了一个键之后，都会对脏键计数器的值增1，这个计数器会触发服务器的持久化以及复制操作
6>如果服务器开启了数据库通知功能，那么在对键进行修改之后，服务器将按配置发送相应的数据库通知
    
### 9.4 设置键的生存时间或过期时间
通过expire命令或者pexpire命令，客户端可以以秒或者毫秒精度为数据库中的某个键设置生存时间（Time To Live）
TTL,在经过指定的秒数或者毫秒数之后，服务器就会自动删除生存时间为0的键

setex命令可以在设置一个字符串键的同时为键设置过期时间

TTL命令和PTTL命令接受一个带有生存时间或过期时间的键，返回这个键的剩余生存时间

#### 9.4.1 设置过期时间
EXPIRE key ttl命令用于将键key的生存时间设置为ttl秒
PEXPIRE key ttl命令用于将键key的生存时间设置为ttl毫秒
EXPIREAT key timestamp命令用于将键key的过期时间设置为timestamp所指定的秒数时间戳
PEXPIREAT key timestamp命令用于将键key的过期时间设置为timestamp所指定的毫秒数时间戳

实际上EXPIRE、PEXPIRE、EXPIREAT 三个命令都是使用PEXPIREAT命令来实现的


#### 9.4.2 保存过期时间
redisDb结构的expires字典保存了数据库中所有键的过期时间，我们称这个字典为过期字典
```
typedef struct redisDb {
    //...
    //过期字典，保存着键的过期时间
    dict *expires;
    //...
}redisDb;
```
1>过期字典的键是一个指针，这个指针指向键空间中某个键对象(也即是某个数据库键)
2>过期字典的值是一个long long类型的整数，这个整数保存了键所指向的数据库键的过期时间——一个毫秒精度的UNIX时间戳

#### 9.4.3 移除过期时间
persist命令可以移除一个键的过期时间

#### 9.4.4 计算并返回剩余生存时间
ttl 以秒为单位返回键的剩余生存时间
pttl 以毫秒为单位返回键的剩余生存时间

#### 9.4.5 过期键的判定
通过过期字典，程序可以用以下步骤检查一个给定键是否过期：
1>检查给定键是否存在于过期字典：如果存在，那么取得键的过期时间
2>检查当前UNIX时间戳是否大于键的过期时间，如果是的话，那么键已经过期，否则的话，键未过期
  
  
### 9.5 过期键删除策略
过期键什么时候会被删除？
1>定时删除：在设置键的过期时间的同时，创建一个定时器，让定时器在键的过期时间来临时，立即执行对键的删除操作
2>惰性删除：放任键过期不管，但是每次从键空间中获取键时，都检查取得的键是否过期，如果过期的话，就删除改键，否则就返回改键
3>定期删除：每隔一段时间，程序就对数据库进行一次检查，删除里面的过期键，至于要删除过少过期键，以及要检查多少个数据库，则由算法决定。


#### 9.5.1 定时删除：
对CPU时间是最不友好的。在过期键比较多的情况下，删除过期键这一行为可能会占用相当一部分CPU时间，在内存不紧张但是
CPU时间紧张的情况下，无疑会对服务器的响应时间和吞吐量造成影响
除此之外，创建一个定时器需要用到Redis服务器中的时间事件，而当前时间事件的实现方式——无序链表，查找一个
事件的时间复杂度为O(N)——并不能高效地处理大量时间事件


#### 9.5.2 惰性删除：
对内存不友好

#### 9.5.3 定期删除：
定期删除策略是前两种策略的一种整合和折中
定期删除策略每隔一段时间执行一次删除过期键操作，并通过限制删除操作执行的时长和频率来减少删除操作对CPU时间影响
除此之外，通过定期删除过期键，定期删除策略有效地减少了因为过期键而带来的内存浪费。

### 9.6 Redis的过期删除策略
  Redis服务器实际使用的是惰性删除和定期删除两种策略：通过配合使用这两种删除策略，服务器可以很好
地在合理使用CPU时间和避免浪费内存空间之间取得平衡。
#### 9.6.1 惰性删除策略的实现
  惰性删除策略由expireifNeeded函数实现，所有读写数据库的Redis命令在执行之前都会调用expireIfNeeded函数对输入键进行检查:
如果输入键已经过期，那么expireIfNeeded函数将输入键从数据库中删除
如果输入键未过期，那么expireIfNedded函数不做任何动作。
#### 9.6.2 定期删除策略的实现
  过期键的定期删除策略由activeExpireCycle函数实现，每当Redis的服务器周期性操作serverCron函数执行时，
activeExpireCycle函数就会被调用，它在规定的时间内，分多次遍历服务器中的各个数据库，从数据库的expires字典中随机
检查一部分键的过期时间，并删除其中的过期键
activeExpireCycle函数的工作模式可以总结如下：
1>函数每次运行时，都从一定数量的数据库中取出一定数量的随机键进行检查，并删除其中的过期键
2>全局变量current_db会记录当前activeExpireCycle函数检查的进度，并在下一次activeExpireCycle函数调用时，接着上一次的进度
  进行处理。
3>随着activeExpireCycle函数的不断执行，服务器中的所有数据库都会被检查一遍，这时函数将current_db变量重置为0，然后再次开始
  新一轮的检查工作。

### 9.7 AOF、RDB和复制功能对过期键的处理

#### 9.7.1 生成RDB文件
  在执行SAVE命令或者BGSAVE命令创建一个新的RDB文件时，程序会对数据库中的键进行检查，已过期的键不会被保存到新创建的RDB文件中
#### 9.7.2 载入RDB文件
在启动Redis服务器时，如果服务器开启了RDB功能，那么服务器将对RDB文件进行载入：
1>如果服务器以主服务器模式运行，那么在载入RDB文件时，程序会对文件中保存的键进行检查，未过期的键会被载入到数据库中，
  而过期键则会被忽略，所以过期键对载入RDB文件的主服务器不会造成影响。
2>如果服务器以从服务器模式运行，那么在载入RDB文件时，文件中保存的所有键，不论是否过期，都会被载入到数据库中。不过，
  因为主从服务器在进行数据同步的时候，从服务器的数据库就会被清空，所以一般来讲，过期键对载入RDB文件的从服务器也不会造成影响。
   
#### 9.7.3 AOF文件写入
当服务器以AOF持久化模式运行时，如果数据库中的某个键已经过期，但它还没有被惰性删除或者定期删除，那么AOF文件不会因为这个过期键而产生任何影响。
当过期键被惰性删除或定期删除之后，程序会向AOF文件追加一条DEL命令，来显示地记录该键以被删除
#### 9.7.4 AOF重写
和生成RDB文件时类似，在执行AOF重写的过程中，程序会对数据库中的键进行检查，已过期的键不会被保存到重写后的AOF文件中。
#### 9.7.5 复制
当服务器运行在复制模式下时，从服务器的过期键删除动作由主服务器控制
1>主服务器在删除一个过期键之后，会显式地向从服务器发送一个DEL命令，告知从服务器删除这个过期键。
2>从服务器在执行客户端发送的读命令时，即使碰到过期键也不会将过期键删除，而是继续像处理未过期的键一样来处理过期键
3>从服务只有在接到主服务器发来的DEL命令之后，才会删除过期键。
### 9.8 数据库通知
数据库通知是Redis2.8版本新增加的功能，这个功能可以让客户端通过订阅给定的频道或者模式，来获知数据库中键的变化，以及数据库中命令的执行情况。

这一类关注"某个键执行了什么命令"的通知称为键空间通知除此之外，还有另一类称为键事件通知的通知，他们关注的是“某个命令被什么键执行了”

服务器配置的notify-keyspace-events选项决定了服务器所发送通知的类型
1>想让服务器发送所有类型的键空间通知和键事件通知，可以将选项的值设置为AKE
2>想让服务器发送所有类型的键空间通知，可以将选项的值设置为AK
3>想让服务器发送所有类型的键事件通知，可以将选项的值设置为AE
4>想让服务器只发送和字符串键有关的键空间通知，可以将选项的值设置为K$
5>想让服务器只发送和列表键有关的键事件通知，可以将选项的值设置为El
#### 9.8.1 发送通知
发送数据库通知的功能是由notifyKeyspaceEvent函数实现的
  void notifyKeyspaceEvent(int type,char *event,robj *key,int dbid);
  函数type参数是当前想要发送的通知的类型，程序会根据这个值来判断通知是否就是服务器配置notify-keyspace-events选项所选定的通知类型，
从而决定是否发送通知
  event、key、dbid分别是事件的名称、产生事件的键，以及产生事件的数据库号码，函数会根据type参数以及这三个参数来构建事件通知的内容，以及接收通知的
频道名。
#### 9.8.2 发送通知的实现
notifyKeyspaceEvent函数

### 9.9 重点回顾
1>Redis服务器的所有数据库都保存在redisServer.db数组中，而数据库的数量则由redisServer.dbnum属性保存
2>客户端通过修改目标数据库指针，让它指向redisServer.db数组中的不同元素来切换不同的数据库
3>数据库主要由dict和expires两个字典构成，其中dict字典负责保存键值对，而expires字典则赋值保存键的过期时间
4>因为数据库由字典构成，所以对数据库的操作都是建立在字典操作之上的。
5>数据库的键总是一个字符串对象，而值则可以是任意一种Redis对象类型，包括字符串对象、哈希表对象、集合对象、列表对象和有序集合对象，分别对应字符串键、
  哈希表键、集合键、列表键和有序集合键。
6>expires字典的键指向数据库中的某个键，而值则记录了数据库键的过期时间，过期时间是一个毫秒为单位的UNIX时间戳
7>Redis使用惰性删除和定期删除两种策略来删除过期的键：惰性删除策略只在碰到过期键时才进行删除操作，定期删除策略则每隔一段时间主动查找并删除过期键。
8>执行SAVE命令或者BGSAVE命令所产生的新RDB文件不会包含已经过期的键。
9>执行BGREWRITEAOF命令所产生的重写AOF文件不会包含已经过期的键。
10>当一个过期键被删除之后，服务器会追加一条DEL命令到现有AOF文件的末尾，显示地删除过期键
11>当主服务器删除一个过期键之后，它会向所有从服务器发送一条DEL命令，显示地删除过期键
12>从服务器即使发现过期键也不会自作主张地删除它，而是等待主节点发来DEL命令，这种统一、中心化的过期键删除策略可以保证主从服务器数据的一致性。
13>当Redis命令对数据库进行修改之后，服务器会根据配置想客户端发送数据库通知。

# 第10章 RDB持久化
  我们将服务器中的非空数据库以及它们的键值对统称为数据库状态
  RDB持久化既可以手动执行，也可以根据服务器配置选项定期执行，该功能可以将某个时间点上的数据库状态保存到一个RDB文件中，该文件是一个经过压缩的
二进制文件，通过该文件可以还原生成RDB文件的数据库状态
### 10.1 RDB文件的创建与载入
有两个Redis命令可以用于生成RDB文件，一个是SAVE，另一个是BGSAVE。
SAVE命令会阻塞Redis服务器进程，直到RDB文件创建完毕为止，在服务器进程阻塞期间，服务器不能处理任何命令请求
和SAVE命令直接阻塞服务器进程的做法不同，BGSAVE命令会派生出一个子进程，然后由子进程负责创建RDB文件，服务器进程（父进程）继续处理命令请求
创建RDB文件的实际工作由rdbSave函数完成，SAVE命令和BGSAVE命令会以不同的方式调用这个函数

RDB文件的载入工作是在服务器启动时自动执行的，所以Redis并没有专门用于载入RDB文件的命令，只要Redis服务器在启动时检测到RDB文件存在，它就会自动
载入RDB文件

另外值得一提的是，因为AOF文件更新频率通常比RDB文件更新频率高，所以：
1>如果服务器开启了AOF持久化功能，那么服务器会优先使用AOF文件来还原数据库状态
2>只有在AOF持久化功能处于关闭状态时，服务器才会使用RDB文件来还原数据库状态
  
在BGSAVE命令执行期间：
客户端发送的SAVE命令会被服务器拒绝，避免同时执行rdbSave调用，防止产生竞争条件
客户端发送的BGSAVE命令会被服务器拒绝，避免竞争
如果客户端发送BGREWRITEAOF命令会被延迟到BGSAVE命令执行完毕之后执行
如果BGREWRITEAOF命令正在执行，那么客户端发送的BGSAVE命令会被服务器拒绝
因为BGREWRITEAOF和BGSAVE两个命令的实际工作都由子进程执行，所以这两个命令在操作方面并没有什么冲突的地方，不能同时执行他们只是一个性能方面的考虑

服务器在载入RDB文件期间，会一直处于阻塞状态，知道载入工作完成为止

### 10.2 自动间隔性保存
save 900 1  服务器在900秒内，对数据库进行了至少1次修改
save 300 10 服务器爱300秒内，对数据库进行了至少10次修改
save 60 10000 服务器在60秒之内，对数据库进行了至少10000次修改
这些条件被保存在服务器状态redisServer结构的saveparams属性：
```
struct redisServer {
    //...
    //记录了保存条件的数组
    struct saveparam *saveparam;
    //...
};
```
saveparams属性是一个数组，数组中的每个元素都是一个saveparam结构，
每个saveparam结构都保存了一个save选项设置的保存条件
```
struct saveparam {
    //秒数
    time_t seconds;
    //修改数
    int changes;
}
```
除了saveparams数组之外，服务器还维持着一个dirty计数器，以及一个lastsave属性：
dirty计数器记录距离上一次成功执行SAVE命令或者BGSAVE命令之后，服务器对数据库状态进行了多少次修改
lastsave属性时一个UNIX时间戳，记录了服务器上一次成功执行SAVE命令或者BGSAVE命令的时间。
```
struct redisServer {
    //...
    //修改计数器
    long dirty;
    //上一次执行保存时间
    time_t lastsave;
    
    //...
};
```
Redis的服务器周期性操作函数serverCron默认每隔100毫秒就会执行一次，该
函数用于对正在运行的服务器进行维护，它的其中一项工作就是检查save选项所
设置的保存条件是否已经满足，如果满足的话，就执行BGSAVE命令

### 10.3 RDB文件结构
一个完整RDB文件所包含的各个部分
REDIS db_version databases EOF check_sum

RDB文件的最开头是REDIS部分，这个部分的长度为5字节，保存着“REDIS”五个字符，通过这五个字符，程序可以在载入文件时，快速检查所载入的文件是否RDB文件
db_version长度为4字节，它的值是一个字符串表示的整数，这个整数记录了RDB文件的版本号
databases部分包含着零个或任意多个数据库，以及各个数据库中的键值对数据
1>如果服务器的数据库状态为空（所有数据库都是空的），那么这个部分也为空，长度为0字节
2>如果服务器的数据库状态为非空（有至少一个数据库非空），那么这个部分也为非空，根据数据库所保存的键值对数量、类型和内容不同，这个部分的长度也会有所不同
EOF常量的长度为1字节，这个常量标志RDB文件正文内容的结束，当读入程序遇到这个值的时候，它知道所有数据库的所有键值对都已经载入完毕了。
check_sum是一个8字节长的无符号整数，保存着一个校验和，这个校验和是程序通过对REDIS、db_version、databases、EOF四个部分的内容进行计算得出的。
服务器在载入RDB文件时，会将载入数据所计算出的校验与check_sum所记录的校验和进行对比，依次来检查RDB文件是否有出错或者损坏的情况出现。


#### 10.3.1 databases部分
SELECTDB db_number key_value_pairs

#### 10.3.2 key_value_pairs部分
不带过期时间：
TYPE key value
TYPE常量代表了一种对象类型或者底层编码，程序会根据TYPE的值来决定如何读入和解释value的数据

带过期时间：
EXPIRETIME_MS ms TYPE key value

RDB文件中的每个value部分都保存了一个值对象，每个值对象的类型都由与之对应的TYPE记录，根据类型的不同，value部分的结构，长度也会有所不同


分析RDB文件
od命令来分析Redis服务器产生的RDB文件，该命令可以用给定的格式转存并打印输入文件。

### 10.5 重点回顾
1>RDB文件用于保存和还原Redis服务器所有数据库中的所有键值对数据
2>SAVE命令由服务器进程直接执行保存操作，所以该命令会阻塞服务器
3>BGSAVE命令由子进程执行保存操作，所以该命令不会阻塞服务器
4>服务器状态中会保存所有用save选项设置的保存条件，当任意一个保存条件被满足时，服务器会自动执行BGSAVE命令
5>RDB文件是一个经过压缩的二进制文件，由多个部分组成
6>对于不同类型的键值对，RDB文件会使用不同的方式来保存它们





## 第11章 AOF持久化

与RDB持久化通过保存数据库中的键值对来记录数据库状态不同，AOF持久化是通过保存Redis服务器所执行的写命令来记录数据库状态的

### 11.1 AOF持久化的实现
AOF持久化功能的实现可以分为命令追加、文件写入、文件同步三个步骤
#### 11.1.1 命令追加
当AOF持久化功能处于打开状态时，服务器在执行完一个写命令之后，会以协议格式将被执行的写命令追加到服务器状态的aof_buf缓冲区的末尾
```
struct redisServer {
    //...
    //AOF缓冲区
    sds aof_buf;
    //...
};
```
#### 11.1.2 AOF文件的写入与同步
   Redis的服务器进程就是一个事件循环，这个循环中的文件事件负责接收客户端的命令请求，以及客户端发送命令回复，而时间事件则负责执行像
serverCron函数这样需要定时运行的函数。
   因为服务器在处理文件事件时可能会执行写命令，使得一些内容被追加到aof_buf缓冲区里面，所以在服务器每次
结束一个事件循环之前，它都会调用flushAppendOnlyFile函数
flushAppendOnlyFile函数的行为由服务器配置的appendfsync选项的值来决定：
1>当为always时 将aof_buf缓冲区中的所有内容写入并同步到AOF文件中
2>当为everysec   将aof_buf缓冲区中的所有内容写入到AOF文件中，如果上次同步AOF文件的时间距离现在超过一秒钟，
          那么再次对AOF文件进行同步，并且这个同步操作是由一个线程专门负责执行的
3>当为no 将aof_buf缓冲区中的所有内容写入到AOF中，但并不对AOF文件进行同步，何时同步由操作系统来决定

### 11.2 AOF文件的载入与数据还原
  因为AOF文件里面包含了重建数据库状态所需的所有写命令，所以服务器只要读入并重新执行一遍AOF文件里面保存的写命令，就可以还原服务器关闭之前的数据库状态。
步骤：
1>创建一个不带网络连接的伪客户端（fake client）:因为Redis的命令只能在客户端上下文执行，而载入AOF文件时所使用的命令直接来源于AOF文件而不是
  网络连接，所以服务使用了一个没有网络连接的伪客户端来执行AOF文件保存的写命令
2>从AOF文件中分析并读取出一条写命令
3>使用伪客户端执行被读出的写命令
4>一直执行步骤2和步骤3，知道AOF文件中的所有写命令都被处理完毕为止

### 11.3 AOF重写
   随着时间推移AOF文件体积膨胀，为了解决这个问题，Redis提供了AOF文件重写（rewrite）功能，通过该功能，Redis服务器可以创建一个新AOF文件来
代替现有AOF文件，新旧两个文件所保存的数据库状态相同，但新AOF文件不会包含任何浪费空间的冗余命令，所以新AOF文件的体积通常会比旧AOF文件的体积要小得多

#### 11.3.1 AOF文件重写的实现
首先从数据库重读取键现在的值，然后用一条命令去记录键值对，代替之前记录这个键值对的多条命令，这就是AOF重写功能的实现原理。

   实际上，为了避免在执行命令时造成客户端输入缓冲区溢出，重写程序在处理列表、哈希表、集合、有序集合
这四种可能会带有多个元素的键时，会先检查键所包含的元素数量，如果元素超过了redis.h/REDIS_AOF_REWRITE_ITEMS_PER_CMD
常量，那么重写程序将使用多条命令来记录键的值，而不单单使用一条命令。

REDIS_AOF_REWRITE_ITEMS_PER_CMD为64

#### 11.3.2 AOF后台重写
AOF重写程序aof_rewrite函数将在子进程中执行，因为
1>子进程进行AOF重写期间，服务器进程（父进程）可以继续处理命令请求
2>子进程带有服务器进程的数据副本，使用子进程而不是线程，可以在避免使用锁的情况下，保证数据的安全性

在子进程执行AOF重写期间，服务器进程需要执行以下三个工作
1>执行客户端发来的命令
2>将执行后的写命令追加到AOF缓冲区
3>将执行后的写命令追加到AOF重写缓冲区

这样一来可以保证：
1>AOF缓冲区的内容会定期被写入和同步到AOF文件，对现有AOF文件的处理工作会如常进行。
2>从创建子进程开始，服务器执行的所有写命令都会被记录到AOF重写缓冲区里面

当子进程完成AOF重写工作之后，它会向父进程发送一个信号，父进程在接到该信号之后，会调用一个信号处理函数，并
执行以下工作：
1>将AOF重写缓冲区中的所有内容写入到新AOF文件中，这时新AOF文件所保存的数据库状态和服务器当前的数据库状态一致
2>对新AOF文件进行改名，原子地覆盖现有的AOF文件，完成新旧两个AOF文件的替换
这个信号处理函数执行完毕之后，父进程就可以继续像往常一样接受命令请求了。
在整个AOF后台重写过程中，只有信号处理函数执行时会对服务器进程（父进程）造成阻塞，在其他时候，AOF
后台重写都不会阻塞父进程，这将AOF重写对服务器性能造成的影响将到了最低

以上就是BGREWRITEAOF命令的实现原理



### 11.4 重点回顾
1>AOF文件通过保存所有修改数据库的写命令请求来记录服务器的数据库状态
2>AOF文件中的所有命令都以Redis命令请求协议的格式保存
3>命令请求会先保存到AOF缓冲区里面，之后在定期写入并同步到AOF文件
4>appendfsync选项的不同值对AOF持久化功能的安全性以及Redis服务器的性能有很大的影响
5>服务器只要载入并重新执行保存在AOF文件中的命令，就可以还原数据库本来的状态
6>AOF重写可以产生一个新的AOF文件，这个新的AOF文件和原有的AOF文件所保存的数据库状态一样，但体积更小。
7>AOF重写是一个有歧义的名字，该功能是通过读取数据库中的键值对来实现的，程序无必须对现有AOF文件进行任何读入、分析或者写入操作。
8>在执行BGREWRITEAOF命令时，Redis服务器会维护一个AOF重写缓冲区，该缓冲区会在子进程创建新AOF文件期间，记录服务器执行的所有写命令。当子进程
  完成创建新AOF文件的工作之后，服务器会将重写缓冲区中的所有内容追加到新AOF文件的末尾，使得新旧两个AOF文件所保存的数据库状态一致。最后，服务器
  用新的AOF文件替换旧的AOF文件，以此来完成AOF文件重写操作。


## 第12章 事件

Redis服务器是一个事件驱动程序，服务器需要处理以下两类事件：
文件事件(file event):Redis服务器通过套接字与客户端（或者其他Redis服务器）进行连接，而文本事件
                   就是服务器对套接字操作的抽象。服务器与客户端（或者其他服务器）的通信会产生相应的文件
                   事件，而服务器则通过监听并处理这些事件来完成一系列网络通信操作
时间事件(time event):Redis服务器中的一些操作（比如serverCron函数）需要在给定的时间点执行，而时间
                   事件就是服务器对这类定时操作的抽象。
                   
                                      
### 12.1 文件事件
Redis基于Reactor模式开发了自己的网络事件处理器：这个处理器被称为文件事件处理器(file event handler):
1>文件事件处理器使用I/O多路复用程序来同时监听多个套接字，并根据套接字目前执行的任务来为套接字关联不同的事件处理器。
2>当被监听的套接字准备好执行连接应答(accept)、读取（read）、写入（write）、关闭（close）等操作
  时，与操作相对应的文件事件就会产生，这时文件事件处理器就会调用套接字之前关联好的事件处理器来处理这些事件

   虽然文件事件处理器以单线程方式运行，但通过使用I/O多路复用程序来监听多个套接字，文件事件处理器既实现了高性能的网络通信模型，又可以很好地
与Redis服务器中其他同样以单线程方式运行的模块进行对接，这保持了Redis内部单线程设计的简单性。    
    
#### 12.1.1 文件事件处理器的够成
由四部分够成，分别是套接字、I/O多路复用程序、文件事件分派器（dispatcher）,以及事件处理器

   文件事件是对套接字操作的抽象，每当一个套接字准备好执行连接应答、写入、读取、关闭等操作时，就会产生一个
文本事件，因为一个服务器通常会连接多个套接字，所以多个文件事件有可能会并发地出现。
   I/O多路复用程序负责监听多个套接字，并向文件事件分派器传送那些产生了事件的套接字。
   尽管多个文件事件可能会并发地出现，但I/O多路复用程序总是会将所有产生事件的套接字都放到一个队列里面，
然后通过这个队列，以有序、同步、每次一个套接字的方式向文件事件分派器传送套接字。当上一个套接字产生的
事件被处理完毕之后(该套接字为事件所关联的事件处理器执行完毕)，I/O多路复用程序才会继续向文件事件分派器
传送下一个套接字

I/O多路复用程序的实现
Redis的I/O多路复用程序的所有功能都是通过包装常见的select\epoll\evport和kqueue这些I/O多路复用
函数库来实现的，每个I/O多路复用函数库在Redis源码中都对应一个单独的文件。
因为Redis为每个I/O多路复用函数库都实现了相同的API，所以I/O多路复用程序的底层实现是可以互换的。
Redis在I/O多路复用程序的实现源码中用#include宏定义了相应的规则，程序会在编译时自动选择系统中性能
最高的I/O多路复用函数库来作为Redis的I/O多路复用程序的底层实现

#### 12.1.2 I/O多路复用程序的实现
   Redis的I/O多路复用程序的所有功能都是通过包装常见的select、epoll、evport和kqueue这些I/O多路复用函数库来实现的，每个I/O多路复用函数库在
Redis源码中都对应一个单独的文件。
   因为Redis为每个I/O多路复用函数库都实现了相同的API,所以I/O多路复用程序的底层实现是可以互换的

#### 12.1.3 事件类型
  I/O多路复用程序允许服务器同时监听套接字的AE_READABLE事件和AE_WRITABLE事件，如果一个套接字同时产生了这两种事件，那么文件事件分派器会优先
处理AE_READABLE事件，等到AE_READABLE事件处理完之后，才处理AE_WRITABLE事件，这两类事件和套接字操作之间的对应关系如下：
1>当套接字变得可读时(客户端对套接字执行write操作，或者执行close操作),或者有新的可应答(acceptable)套接字出现时(客户端对服务器的监听套接字执行
  connect操作)，套接字产生AE_READABLE事件。
2>当套接字变得可写时(客户端对套接字执行read操作)，套接字产生AE_WRITABLE事件。

#### 12.1.4 API
#### 12.1.5 文件事件的处理器
Redis为文件事件编写了多个处理器，这些事件处理器分别用于实现不同的网络通信需求，比如说：
1>为了对连接服务器的各个客户端进行应答，服务器要为监听套接字关联应答处理器
2>为了接收客户端传来的命令请求，服务器要为客户端套接字关联命令请求处理器
3>为了向客户端返回命令的执行结果，服务器要为客户端套接字管理命令回复处理器
4>当主服务器和从服务器进行赋值操作时，主从服务器都需要关联特别为复制功能编写的复制处理器

### 12.2 时间事件
Redis的时间事件分为以下两类：
定时事件：让一段程序在指定的时间之后执行一次
周期性事件：让一段程序每隔指定时间就执行一次

一个时间事件主要由以下三个属性组成：
id:服务器为时间事件创建的全局唯一ID(标识号)。ID号按从小到达的顺序递增，新事件的ID号比旧事件的ID号要大
when:毫秒精度的UNIX时间戳，记录时间事件的到达时间
timeProc:时间事件处理器，一个函数。当时间事件到达时，服务器就会调用相应的处理器来处理事件

一个时间事件是定时事件还是周期性事件取决于时间事件处理器的返回值：
如果事件处理器返回ae.h/AE_NOMORE,那么这个事件为定时事件：该事件在达到一次之后就会被删除，之后不再到达
如果事件处理器返回一个非AE_NORMOE的整数值，那么这个事件为周期性事件

当一个时间事件到达之后，服务器会根据事件处理器返回的值，对时间事件的when属性进行更新，让这个事件在一段时间
之后再次到达，并以这种方式一直更新并运行下去。比如说，如果一个时间事件的处理器返回整数值30，那么服务器应该对
这个时间事件进行更新，让这个事件在30毫秒之后再次到达。

#### 12.2.1 实现
服务器将所有时间事件都放在一个无序链表中，每当时间事件执行器运行时，它就遍历整个链表，查找所有已经到达的时间事件，并调用相应的事件处理器。

新的时间事件总是插入到链表的表头，所以事件按ID逆序排序
注意，我们说保存时间事件的链表为无序链表，值得不是链表不按ID排序，而是说，该链表不按when属性的大小排序。正因为链表没有按 when属性进行排序，所以当
时间事件执行器运行的时候，它必须遍历链表中的所有时间事件，这样才能确保服务器中所有已到达的时间事件都会被处理

   在目前版本中，正常模式下的Redis服务器只使用serverCron一个时间事件，而在benchmark模式下，服务器也只使用两个时间事件。在这种情况下，
服务器几乎是将无序链表退化成一个指针来使用 ，所以使用无序链表来保存时间事件，并不影响时间事件执行的性能。


#### 12.2.2 API
#### 12.2.3 时间事件应用实例：serverCron函数
持续运行的Redis服务器需要定期对自身的资源和状态进行检查和调整，从而
确保服务器可以长期、稳定地运行，这些定期操作由serverCron函数负责执行，它
的主要工作包括：
 更新服务器的各类统计信息，比如时间、内存占用、数据库占用情况等
 清理数据库中的过期键值对
 关闭和清理连接失效的客户端
 尝试进行AOF或RDB持久化操作
 如果服务器是主服务器，那么对从服务器进行定期同步
 如果处于集群模式，对集群进行定期同步和连接测试


事件的调度与执行规则：
1>aeApiPoll函数的最大阻塞事件由到达时间最接近当前时间的时间事件决定，这个方法
既可以避免服务器对时间事件进行频繁的轮询（忙等待），也可以确保aeApiPoll
函数不会阻塞过长时间
2>因为文件事件是随机出现的，如果等待并处理完一次文件事件之后，仍未有任何
时间事件到达，那么服务器将再次等待并处理文件事件。随着文件事件的不断执行，
时间会逐渐向时间事件所设置的到达时间逼近，并最终来到到达时间，这时服务器
就可以开始处理到达时间事件了。
3>对文件事件和时间事件的处理都是同步、有序、原子地执行的，服务器不会中途中断
事件处理，也不会对事件进行抢占，因此，不管是文件事件的处理器，还是时间事件的
处理器，它们都会尽可能地减少程序的阻塞时间，并在有需要时主动让出执行权，从而降低
造成事件饥饿的可能性。比如说，在命令回复处理器将一个命令回复写入到客户端套接字时，
如果写入字节数超过了一个预设常量的话，命令回复处理器就会主动用break跳出
写入循环，将余下的数据留到下次再写；另外，时间事件也会将非常耗时的持久化操作
放到子线程或者子进程执行。
4>因为时间事件在文件事件之后执行，并且事件之间不会出现抢占，所以时间事件的实际处理
时间，通常会比时间事件设定的到达时间稍晚一些。

#5 客户端
Redis服务器是典型的一对多服务器程序：一个服务器可以与多个客户端建立网络连接，每个客户端可以向服务器
发送命令请求，而服务器则接收并处理客户端发送的命令请求，并向客户端返回命令回复。

通过使用由I/O多路复用技术实现的文件事件处理器，Redis服务器使用单线程单进程的方式来处理命令请求，并与多个客户端
进行网络通信。

对于每个于服务器进行连接的客户端，服务器都为这些客户端建立了相应的redis.h/redisClient结构(客户端状态)，
这个结构保存了客户端当前的状态信息，以及执行相关功能时需要用到的数据结构，其中包括：
客户端的套接字描述符
客户端的名字
客户端的标志值（flag）
指向客户端正在使用的数据库的指针，以及该数据库的号码
客户端当前要执行的命令、命令的参数、命令参数的个数，以及指向命令实现函数的指针
客户端的输入缓冲区和输出缓冲区
客户端的复制状态信息，以及进行复制所需的数据结构
客户端执行BRPOP、BLPOP等列表阻塞命令时使用的数据结构
客户端的事务状态，以及执行WATCH命令时用到的数据结构
客户端执行发布与订阅功能时用到的数据结构
客户端的身份验证标志
客户端的创建时间，客户端和服务器最后一次通信的时间，以及客户端的输出缓冲区大小超出软性限制的时间

Redis服务器状态结构的clients属性是一个链表，这个链表保存了所有与服务器连接的客户端的状态结构，
对客户端执行批量操作，或者查找某个指定的客户端，都可以通过遍历clients链表来完成
struct redisServer {
    //...
    //一个链表，保存了所有客户端状态
    list *clients;
    //...
};
客户端状态包含的属性可以分为两类：
一类是比较通用的属性，这些属性很少与特定功能相关，无论客户端执行的是什么工作，它们都要用到这些属性。
另外一类是特定功能相关的属性，比如操作数据时需要用到的db属性和dictid属性，执行事务时需要用到的master
属性，以及执行WATCH命令时需要用到的watched_keys属性等等

套接字描述符
客户端状态的fd属性记录了客户端正在使用的套接字描述符
typedef struct redisClient{
    //...
    int fd;
    //...
} redisClient;
根据客户端类型的不同，fd属性的值可以是-1或者是大于-1的整数：
   
   伪客户端(fake client)的fd属性的值为-1：伪客户端的命令请求来源于AOF文件或者Lua脚本，而不是网络，
所以这种客户端不需要套接字连接，自然也不需要记录套接字描述符。目前Redis服务器会在两个地方用到伪客户端，
一个用于载入AOF文件并还原数据库状态，而另一个则用于执行Lua脚本中包含的Redis命令。
   
   普通客户端的fd属性的值为大于-1的整数：普通客户端使用套接字来与服务器进行通信，所以服务器会用fd属性
   来记录客户端套接字的描述符

执行client list命令可以列出目前所有连接到服务器的普通客户端，命令输出中的fd域显示了服务连接客户端所
使用的套接字描述符：


名字在默认情况下，一个连接到服务器的客户端是没有名字的
使用client setname命令可以为客户端设置一个名字
typedef struct redisClient{
    //...
    robj *name;
    //...
} redisClient;
如果客户端没有为自己设置名字，那么相应客户端状态的name属性指向NULL指针；相反地，如果客户端为自己设置
了名字，那么name属性将指向一个字符串对象，而该对象就保存着客户端的名字


标志
客户端的标志属性flags记录客户端的角色(role),以及客户端目前所处的标志：
typedef struct redisClient {
    //...
    int flags;
    //...
} redisClient;

flags属性的值可以是单个标志：
flags = <flag>
也可以是多个标志的二进制，比如：
flags = <flag1> | <flag2> | ...

每个标志使用一个常量表示，一部分标志记录了客户端的角色：
   REDIS_MASTER标志客户端代表的是一个主服务器
   REDIS_SLAVE标志客户端代表的是一个从服务器
   REDIS_PRE_PSYNC标志表示客户端代表的是一个版本低于Redis2.8的从服务器
   REDIS_LUA_CLIENT标识表示客户端是专门用于处理Lua脚本里面包含的Redis命令的伪客户端
而另外一部分标志则记录可客户端目前所处的状态：
   REDIS_MONITOR标志表示客户端正在执行MONITOR命令
   REDIS_UNIX_SOCKET标志表示服务器使用UNIX套接字来连接客户端
   REDIS_BLOCKED表示客户端正在被BRPOP\BLOPO等命令阻塞
   REDIS_UNBLOCKED表示客户端已经从REDIS_BLOCKED标识所表示的阻塞状态中脱离出来，不在阻塞
   REDIS_MULTI表示客户端正在执行事务
   REDIS_DIRTY_CAS表示事务使用WATCH命令监视数据库键已经被修改，REDIS_DIRTY_EXEC表示事务在命令
   入队时出现错误，以上两个标志都表示事务的安全性已经被破坏，只要这两个标记中的任意一个被打开，EXEC命令
   必然会执行失败。
   REDIS_CLOSE_ASAP标志表示客户端的输出缓冲区大小超过了服务器允许的范围。服务器在下一次执行serverCron
   函数时关闭这个客户端
   REDIS_CLOSE_AFTER_REPLY标志表示有用户对这个客户端执行了client kill命令，或者客户端发送
   给服务器的命令请求中包含了错误的协议内容。服务器会将客户端寄存在输出缓冲区中的所有内容发送给客户端，然后关闭客户端
   REDIS_ASKING标志表示客户端向集群节点发送了ASKING命令
   REDIS_FORCE_AOF标志强制服务器将当前执行的命令写入到AOF文件里面。REDIS_FORCE_REPL标志强制
   主服务器将当前执行的命令复制给所有从服务器。执行PUBSUB命令会使客户端打开REDIS_FORCE_AOF标志，
   执行SCRIPT LOAD命令会使客户端打开REDIS_FORCE_AOF标志和REDIS_FORCE_REPL标志
   在主服务器进行命令传播期间，从服务器需要向主服务器发送REPLICATION ACK命令，在发送这个命令之前，从
   服务器必须打开主服务器对应的客户端的REDIS_MASTER_FORCE_REPLAY标志，否则发送操作会被拒绝执行。
   
   
输入缓冲区
客户端状态的输入缓冲区用于保存客户端发送的命令请求
typedef struct redisCient{
    //...
    sds querybuf;
    //...
} redisClient;
输入缓冲区的大小会根据输入内容动态地缩小或者扩大，但它的最大大小不能超过1GB,否则服务器将关闭这个客户端

命令与命令参数
在服务器将客户端发送的命令请求保存到客户端状态的querybuf属性之后，服务器将对命令请求的内容进行分析，
并将得出的命令参数以及命令参数的个数分别保存到客户端状态的qrgv属性和argc属性
typedef struct redisClient {
    //...
    robj **argv;
    
    int argc;
    
    //...
} redisClient;
argv属性是一个数组，数组中的每个项都是一个字符串对象，其中argv[0]是要执行的命令，而之后的其他项目则是传给命令的参数
argc属性则负责记录argv数组的长度。

命令的实现函数
当服务器从协议内容中分析并得出argv属性和argc属性的值之后，服务器将根据项argv[0]的值，在命令表中查找命令所对应的命令实现函数
命令表是一个字典，字典的键是一个SDS结构，保存了命令的名字，字典的值是命令所对应的redisCommand结构，这个结构保存了命令的实现
函数、命令的标志、命令应该给定的参数个数、命令的总执行次数和总消耗时长等统计信息。
当程序在命令表中成功找到argv[0]所对应的redisCommand结构时，它会将客户端状态的cmd指针指向这个结构
typedef struct redisClient{
    //...
    struct redisCommand *cmd;
    //...
}redisClient;

针对命令表查找的操作不区分输入字母的大小写，所以无论argv[0]是“SET”、“set”、或者“SeT”等等，查找的结果都是相同的。

输出缓冲区
执行命令所得的命令回复会被保存在客户端状态的输出缓冲区里面，每个客户端都有
两个输出缓冲区可用，一个缓冲区的大小是固定的，另一个缓冲区的大小是可变的
固定大小的缓冲区用于保存那些长度比较小的回复，比如OK、简短的字符串值、整数值、错误回复等等
可变大小的缓冲区用于保存那些长度比较大的回复，比如一个非常长的字符串值，一个由很多项
组成的列表，一个包含了很多元素的集合等等
客户端的固定大小缓冲区由buf和bufpos两个属性组成：
typedef struct redisClient{
    //...
    char buf[REDIS_REPLY_CHUNK_BYTES];
    
    int bufpos;
    
    //...
} redisClient;
buf是一个大小为REDIS_REPLY_CHUNK_BYTES字节的字节数组，而bufpos属性
则记录了buf数组目前已使用的字节数量
REDIS_REPLY_CHUNK_BYTES常量目前的默认值为16*1024，也即是说，buf数组的默认大小为16KB
可变大小缓冲区由reply链表和一个或多个字符串对象组成
typedef struct redisClient{
    //...
    list *reply;
    //...
} redisClient;
通过使用链表来连接多个字符串对象，服务器可以为客户端保存一个非常长的命令回复，
而不必受到固定大小缓冲区16KB大小的限制。

身份验证
客户端状态的authenticated属性用于记录客户端是否通过了身份验证：
typedef struct redisClient {
    //...
    int authenticated;
    //...
} redisClient;
如果authenticated的值为0，那么表示客户端未通过身份验证；如果authenticated的值为1，
那么表示客户端已经通过了身份验证
当客户端authenticated属性的值为0时，除了AUTH命令之外，客户端发送的所有其他
命令都会被服务器拒绝执行
当客户端通过auth命令成功进行身份验证之后，客户端状态authenticated属性
的值就会从0变为1
authenticated属性仅在服务器启用了使用身份验证功能时使用。如果服务器没有
启用身份验证的话，那么即使authenticated属性的值为0，服务也不会拒绝执行客户端
发送的命令请求


时间
最后，客户端还有几个和时间有关的属性：
typedef struct redisClient{
    //...
    time_t ctime;
    time_t lastinteraction;
    time_t obuf_soft_limit_reached_time;
    //...
}redisClient;
ctime属性记录了创建客户端的时间，这个时间可以用来计算客户端与服务器已经连接
连接了多少秒，client list命令的age域记录了这个秒数
lastinteraction属性记录了客户端与服务器最后一次进行互动的时间，这里
的互动可以是客户端向服务器发送命令请求，也可以是服务器向客户端发送命令回复
lastinteraction属性可以用来计算客户端的空转时间，也即是，距离客户端与服务器
最后一个进行互动以来，已经过去了多少秒，client list命令的idle域记录了这个秒数
obuf_soft_limit_reached_time属性记录了输出缓冲区第一次到达软性限制的时间


客户端的创建于关闭
服务器使用不同的方式来创建和关闭不同类型的客户端
创建普通客户端
如果客户端是通过网络连接与服务器进行连接的普通客户端，那么在客户端使用connect函数
连接到服务器时，服务器就会调用连接事件处理器，为客户端创建相应
的客户端状态，并将这个新的客户端添加到服务器状态结构clients链表的末尾
关闭普通客户端
一个普通客户端可以因为多种原因被关闭
如果客户端进程退出或者被杀死，那么客户端与服务器之间的网络连接将被关闭，从而造成客户端被关闭
如果客户端向服务器发送带有不符合协议格式的命令请求，那么这个客户端也会被服务器关闭
如果客户端成为了client kill命令的目标，那么它也会被关闭
如果用户为了服务器设置了timeout配置选项，那么当客户端 的空转时间超过timeout选项设置的值时，
客户端将被关闭。不过timeout选项有些例外情况，如果客户端是主服务，从服务器正在被BLPOP命令阻塞或者
正在执行SUBSCRIBE、PUBSCRIBE等订阅命令，那么即使客户端的空转时间超过了timeout选项的值，客户端也不会被服务器关闭
如果客户端发送的命令请求的大小超过了输入缓冲区的限制大小（默认为1GB），那么这个客户端会被服务器关闭
如果要发送给客户端的命令回复大小超过了输出缓冲区的限制大小，那么这个客户端会被服务器关闭
前面介绍输出缓冲区的时候提到，可变大小缓冲区由一个链表和任意多个字符串对象组成，理论上来说，这个缓冲区
可以保存任意长的命令回复
但是，为了避免客户端的回复过大，占用过多的服务器资源，服务器会时刻检查客户端输出缓冲区的大小，并在缓冲区大小超出范围时，执行相应的限制操作
服务器使用两种模式来限制客户端输出缓冲区的大小
硬性限制：如果输出缓冲区大小超过了硬性限制所设置的大小，那么服务器立刻关闭客户端
软性限制：如果输出缓冲区的大小超过了软性限制所设置的大小，但还没有超过硬性限制，那么服务器
将使用客户端结构的obuf_soft_limit_reached_time属性记录下客户端到达软性限制的起始时间，之后服务器会继续监视
客户端，如果输出缓冲区的大小一致超出软性限制，并且持续时间超过服务器设定的时长，那么服务器将关闭客户端；相反地，
如果输出缓冲区大小在指定时间之内，不再超过软性限制，那么客户端就不会被关闭，并且obuf_soft_limit_reached_time
属性的值也会被清零
通过client_ouput_buffer_limit来进行设置

Lua脚本的伪客户端
服务器会在初始化时创建负责执行Lua脚本中包含Redis命令的伪客户端，并将这个伪客户端关联在服务状态结构的lua_client属性中
struct redisServer {
    //...
    redisClient *lua_client;
    //...
};
lua_client伪客户端在服务器运行的整个生命周期中会一直存在，只有服务器被关闭时，这个客户端才会被关闭


AOF文件的伪客户端
服务器在载入AOF文件时，会创建用于执行AOF文件包含的Redis命令的伪客户端，并在载入完成之后，关闭这个伪客户端

#6 服务器
命令请求的执行过程
一个命令从请求从发送到获得回复的过程中，客户端和服务器需要完成一系列操作。
比如执行SET KEY VALUE命令到获得回复OK期间，需要经过以下步骤：
1> 客户端向服务器发送命令请求SET KEY VALUE
2> 服务器接收并处理客户端发来的命令请求SET KEY VALUE,在数据库中进行设置操作，并产生命令回复ok.
3>服务器将命令回复OK发送给客户端
4>客户端接收服务器返回的命令回复OK,并将这个回复打印给用户观看。


发送命令请求
Redis服务器的命令请求来自Redis客户端，当用户在客户端中键入一个命令请求时，客户端会将这个命令
请求转换成协议格式、然后通过连接到服务器的套接字，将协议格式的命令请求发送给服务器
举例：
客户端键入SET KEY VALUE
那么客户端会将这个命令转换成协议：
*3\r\n$3\r\n\SET\r\n$3\r\nKEY\r\n$5\r\nVALUE\r\n
然后将这段协议内容发送给服务器


读取命令请求
当客户端与服务器之间的连接套接字因为客户端的写入而变得可读时，服务器将调用命令请求处理器来执行以下操作：
1>读取套接字中协议的命令请求，并将其保存到客户端状态的输入缓冲区里面。
2>对输入缓冲区中的命令请求进行分析，提取出命令中包含的命令参数，以及命令参数的个数，然后分别将参数和参数
的个数保存到客户端状态的argv属性和argc属性里面
3>调用命令执行器，执行客户端指定的命令

命令执行器（1）：查找命令实现
命令执行器要做的第一件事就是根据客户端状态的argv[0]参数，在命令表(command talbe)中查找参数所指定
的命令，并将找到的命令保存到客户端状态的cmd属性里面。
命令表是一个字典，字典的键是一个个命令名字，比如“set”、“get”、“del”等等；而字典的值则是一个个redisCommand结构，
每个redisCommand结构记录了一个Redis命令的实现信息

命令执行器（2）：执行预备操作
到目前为止，服务器已经将执行命令所需的命令实现函数（保存在客户端状态的cmd属性）、参数(保存在客户端状态的argv属性)、
参数个数(保存在客户端状态的argc属性)都手机齐了，但是在真正执行命令之前，程序还需要进行一些预备操作，从而确保命令可以正确、
顺利地执行，这些操作包括：
检查客户端状态的cmd指针是否指向NULL,如果是的话，那么说明用户输入的命令名字找不到相应的命令实现，服务器不再执行后续步骤，并向客户端返回一个错误
根据客户端cmd属性指向的redisCommand结构的arity属性，检查命令请求所给定的参数个数是否正确，当参数个数不正确时，不再执行后续步骤，直接向客户端返回一个错误。
比如说，如果arity属性的值为-3，那么用户输入的命令参数个数必须大于等于3个才行
检查客户端是否已经通过了身份 验证，未验证通过的客户端只能执行auth命令。如果执行其他，那么服务器将向客户端返回个错误
如果服务器打开了maxmemory功能，那么在执行命令之前，先检查服务器的内存占用情况，并在有需要时进行内存回收，从而使得接下来的命令
可以顺利执行。如果内存回收失败，那么不再执行后续步骤，向客户端返回一个错误
如果服务器上一次执行BGSAVE命令时出错，并且服务器即将要执行的命令是一个写命令，那么服务器将拒绝执行这个命令，并向客户端返回一个错误
如果客户端当前正在用SUBSCRIBE命令订阅频道，或者正在用PSUBSCRIBE命令订阅模式，那么服务器只会执行客户端发来的SUBSCRIBE、PSUBSCRIBE\
UNSUBSCRIBE\PUNSUBSCRIBE四个命令，其他命令都会被服务器拒绝
如果服务器正在进行数据载入，那么客户端发送的命令必须带有1标识（INFO\SHUTDONW\PUBLISH等等）才会被服务器执行，其他命令都会被服务器拒绝
如果服务器因为执行Lua脚本而超时并进入阻塞状态，那么服务器只会执行客户端发来的SHUTDOWN nosave命令和SCRIPT KILL命令，其他命令都会被服务器拒绝
如果客户端正在执行事务，那么服务器只会执行客户端发来的EXEC\DISCARD\MULTI\WATCH四个命令，其他命令都会被放进事务队列中
如果服务器打开了监视器功能，那么服务器会将要执行的命令和参数等信息发送给监视器。当完成了以上预备操作之后，服务器就可以开始真正执行命令了

以上只列出了服务器在单机模式下执行命令时的检查操作，当服务器在复制或者集群模式下执行命令时，预备操作还会更多一些。

命令执行器（3）：调用命令的实现函数
当服务器决定要执行命令时，它只要执行 以下语句就可以了：
//client是指向客户端状态的指针
client->cmd->proc(client);
等于执行语句：
setCommand(client)
被调用的命令实现函数会执行指定的操作，并产生相应的命令回复，这些回复会被保存在客户端状态的输出缓冲区里面(buf属性和reply属性)，
之后实现函数还会为客户端的套接字关联命令回复处理器，这个处理器负责将命令回复返回给客户端
对于前面SET命令的例子，函数调用setCommand(client)将产生一个“+OK\r\n”回复，这个回复会被保存到客户端状态的buf属性里面

命令执行器（4）：执行后续工作
在执行完实现函数之后，服务器还需要执行一些后续工作：
如果服务器开启了慢查询日志功能，那么慢查询日志模块会检查是否需要为刚刚执行完的命令请求添加一条新的慢查询日志。
根据刚刚执行命令所耗费的时长，更新被执行命令的redisCommand结构的milliseconds属性，并将命令的redisCommand结构的calls计数器的值增一。
如果服务器开启了AOF持久化功能，那么AOF持久化模块会将刚刚执行的命令请求写入到AOF缓冲区里面
如果有其他从服务器正在复制当前这个服务器，那么服务器会将刚刚执行的命令传播给所有从服务器

当以上操作都执行完之后，服务器对于当前命令的执行到此就告一段落了，之后服务器就可以继续从文件处理器中取出并处理下一个命令请求了。


将命令回复发送给客户端
命令实现函数会将命令回复保存到客户端的输出缓冲区里面，并为客户端的套接字关联命令回复处理器，当客户端套接字变为可写状态时，服务器就会执行
命令回复处理器，将保存在客户端输出缓冲区中的命令回复发送给客户端。
当命令回复发送完毕之后，回复处理器会清空客户端状态的输出缓冲区，为处理下一个命令请求做好准备。


客户端接收并打印命令回复
当客户端接收到协议格式的命令回复之后，它会将这些回复转换成人类可读的格式，并打印给用户观看

serverCron函数
Redis服务器中的serverCron函数默认每隔100毫秒执行一次，这个函数负责管理服务器的资源，并保持服务器自身良好运转。

更新服务器时间缓存
Redis服务器中有不少功能需要获取系统的当前时间，而每次获取系统的当前时间都需要执行一次系统调用，为了减少系统调用的执行次数，服务器
状态中的unixtime属性和mstime属性被作用当前时间的缓存：
struct redisServer {
    //...
    //保存了秒级精确的系统当前UNIX时间戳
    time_t unixtime;
    
    //保存了毫秒级精确的系统当前UNIX时间戳
    long long mstime;
    
    //...
};
因为serverCron函数默认会以每100毫秒一次的频率更新unixtime属性和mstime属性，所以这两个属性记录的时间精确度并不高：
服务器只会在打印日志、更新服务器的LRU时钟、决定是否执行持久化任务、计算服务器上线时间这类对时间精确度要求不高的功能上
对于为键设置过期时间、添加慢查询日志这种需要高精确度时间的功能来说，服务器还是会再次执行系统调用，从而获取最准确的系统当前时间。

更新LRU时钟
服务器状态中的lruclock属性保存了服务器的LRU时钟，这个属性和上面介绍的unixtime属性、mstime属性一样，都是服务器时间缓存的一种：
struct redisServer {
    //...
    //默认每10秒更新一次的时钟缓存
    //用于计算键的空转(idle)时长
    unsigned lruclodk:22;
    //...
};
每个Redis对象都会有一个lru属性，这个lru属性保存了对象最后一次被命令访问的时间：
typedef struct redisObject{
    //...
    unsigned lru:22;
    //...
    
}robj;
当服务器要计算一个数据库键的空转时间（也即是数据库键对应的值对象的空转时间），程序会用服务器的lruclock属性记录的时间
减去对象的lru属性记录的时间，得出的计算结果就是这个对象的空转时间
object idletime key
serverCron函数默认会以每10秒一次的频率更新lruclock属性的值，因为这个时钟不是实时的，所以根据这个属性计算出来的LRU
时间实际上只是一个模糊的估算值
lruclock时钟的当前值可以通过info server命令的lru_clock域查看


更新服务器每秒执行命令次数
serverCron函数中的trackOperationsPerSecond函数会以每100毫秒一次的频率执行，这个函数的功能是以抽样计算的方式，
估算并记录服务器在最近一秒钟处理的命令请求数量，这个值可以通过info status命令的instantaneous_ops_per_sec域查看


更新服务器内存峰值记录

处理sigterm信号

管理客户端资源

管理数据库资源

执行被延迟的bgrewriteaof

检查持久操作的运行状态

将AOF缓冲区中的内容写入AOF文件

关闭异步客户端

增加cronloops计数器的值



初始化服务器
















































