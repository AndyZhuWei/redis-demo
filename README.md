# redis-demo
##第一部分 数据结构与对象
#1 简单动态字符串
Redis没有直接使用C语言传统的字符串表示（以空字符串结尾的字符数组），而是自己构建了一种名为
简单动态字符串（simple dynamic string,SDS）的抽象类型，并将SDS用作Redis的默认字符串表示

在Redis里面，C字符只会作为字符串字面量用在一些无须对字符串值进行修改的地方，比如打印日志
除了用来保存数据库中的字符串值之外，SDS还被用作缓冲区(buffer)：AOF模块中的AOF缓冲区，以及客户端状态中
的输入缓冲区，都是由SDS实现。

C语言使用的字符串表示方式，并不能满足Redis对字符串在安全性、效率以及功能方面的要求。

* SDS的定义

struct sdshdr {
 //记录buf数组中已使用字节的数量
 //等于SDS所保存字符串的长度
 int len;
 
 //记录buf数组中未使用字节的数量
 int free;
 
 //字节数组，用于保存字符串
 char buf[];

}

SDS遵循C字符串以空字符结尾的惯例是可以直接重用一部分C字符串函数库里面的函数

* SDS与C字符串的区别

  1.常数复杂度获取字符串长度  （C字符串并不记录自身的长度信息）

  2.杜绝缓冲区溢出 
  
  3.减少修改字符串时带来的内存重分配次数（通过为使用空间，SDS实现了空间预分配
  和惰性空间释放两种优化策略）
  
  如果对SDS进行修改之后，SDS的长度（也即是len属性的值）将小于1MB，那么程序分配
  和len属性同样大小的未使用空间，这时SDS len属性的值将和free属性的值相同。
  
  如果对SDS进行修改之后，SDS的长度将大于等于1MB,那么程序会分配1MB的未使用空间。
  
  在扩展SDS空间之前，SDS API会先检查未使用空间是否足够，如果足够的话，API就会直接使用未使用空间，
  而无须执行内存重分配。
  
  惰性空间释放用于优化SDS字符串缩短操作：当SDS的API需要缩短SDS保存的字符串时，
  程序并不立即使用内存重分配来回收缩短后多出来的字节，而是使用free属性将这些字节的数量记录起来，
  并等待将来使用。与此同时，SDS也提供了相应的API,让我们可以在有需要时，真正地释放SDS的未使用空间。
  
  4.二进制安全
  C字符串中的字符必须符合某种编码，并且除了字符串的末尾之外，字符串里面不能包含空字符串，
  否则最先被程序读入的空字符串被误认为是字符串结尾。
  SDS的API都是二进制安全的，所有SDS API都会以处理二进制的方式来处理SDS存放在buf数组里的
  数据，程序不会对其中的数据做任何限制、过滤、或者假设，数据在写入时是什么样的，它被读取时
  就是什么样。这也是我们将SDS的buf属性称为字节数组的原因——Redis不是用这个数组来保存字符，
  而是用它来保存一系列二进制数据。
  
  5 兼容部分C字符串函数
  
  总结：
  images/C字符串与SDS之间的区别.png
  
    

#2 链表
除了链表键之外，发布与订阅、慢查询、监视器等功能也用到了链表，Redis服务器
本身还是用链表来保存多个客户端的状态信息，以及使用链表来构建客户端输出缓冲区。

1.链表和链表节点的实现
typedef struct listNode {
    //前置节点
    struct listNode *prev;
    
    //后置节点
    struct listNode *next;
    
    //节点的值
    void *value
} listNode;

多个listNode可以通过prev和next指针组成双端链表
typedef struct list {
    //表头节点
    listNode *head;
    
    //表尾节点
    listNode *tail;
    
    //链表所包含的节点数量
    unsigned long len;
    
    //节点值复制函数
    void *(*dup)(void *ptr);
    
    //节点值释放函数
    void (*free)(void *ptr);
    
    //节点值对比函数
    int (*match)(void *ptr,void *key);
} list;

Redis的链表实现的特性可以总结如下：
双端
无环
带表头指针和表尾指针
带链表长度计数器
多态


#3 字典


字典，又称为符号表、关联数组或映射，是一种用于保存键值对的抽象数据结构
除了用来表示数据库之外，字典还是哈希键的底层实现之一。

哈希结构：
typedef struct dictht {
    //哈希表数组
    dictEntry **table;
    
    //哈希表大小
    unsigned long size;
    
    //哈希表大小掩码，用于计算索引值
    //总是等于size-1
    unsigned long sizemask;
    
    //该哈希表已有节点的数量
    unsigned long used;
} dictht;
table属性是一个数组，数组中的每个元素都是一个指向dict.h/dictEntry结构的指针

哈希表节点使用dictEntry结构表示
typedef struct dictEntry {
    //键
    void *key;
    
    //值
    union{
        void *val;
        uint64_tu64;
        int64_ts64;
    } v;
    
    //指向下个哈希表节点，形成链表
    struct dictEntry *next;
} dictEntry;

字典结构：
typedef struct dict {
    //类型特定函数
    dictType *type;
    
    //私有数据
    void *privdata;
    
    //哈希表
    dictht ht[2];
    
    //rehash索引
    //当rehash不在进行时，值为-1
    int rehashidx;
}
type属性和privdata属性是针对不同类型的键值对，为创建多态字典而设置
ht属性是一个包含两个项的数组，数组中的每个项都是一个dictht哈希表。一般
情况下，字典只使用ht[0]哈希表，ht[1]哈希表只会在对ht[0]哈希表进行rehash时使用。
除了ht[1]之外，另一个和rehash有关的属性就是rehashidx,它记录了rehash目前的进度，
如果目前没有在进行rehash,那么它的值为-1


2哈希算法
Redis计算哈希值和索引的方法如下：
hash=dict->type->hashFunction(key);
使用哈希表的sizemask属性和哈希值，计算出索引值，根据情况不同，ht[x]可以是
ht[0]或者ht[1]
index=hash&dict->ht[x].sizemask;
Redis使用MurmurHash2算法来计算键的哈希值

3.解决键冲突
链地址法

4.rehash
为了让哈希表的负载因子维持在一个合理的范围之内，当哈希表保存的键值对数量太多
或者太少时，程序需要对哈希表的大小进行相应的扩展或者收缩。
扩展和收缩哈希表的工作可以通过执行rehash操作来完成。Redis对字典的哈希
表执行rehash的步骤如下：
1>为字典的ht[1]哈希表分配空间，这个哈希表空间大小取决于要执行的操作，以及
  ht[0]当前包含的键值对数量（也即是ht[0].used属性的值）：
   如果执行的是扩展操作，那么ht[1]的大小为第一个大于等于ht[0].used*2的2的n次放幂
   如果执行的是收缩操作，那么ht[1]的大小为第一个大于等于ht[0].used的2的n次幂
2>将保存在ht[0]中的所有键值对rehash到ht[1]上面：rehash指的是重新计算键的哈希值和索引值，
然后将键值对放置到ht[1]哈希表的指定位置上。
3>当ht[0]包含的所有键值对都迁移到了ht[1]之后(ht[0]变为空表)，释放ht[0],将
ht[1]设置为ht[0],并在ht[1]新创建一个空白哈希表，为下一次rehash做准备。

哈希表的扩展与缩容
当以下条件中的任意一个被满足时，程序会自动开始对哈希表执行扩展操作：
服务器目前没有在执行BGSAVE命令或者BGREWRITEAOF命令，并且哈希表的负载因子大于等于1
服务器目前正在执行BGSAVE命令或者BGREWRITEAOF命令,并且哈希表的负载因子大于等于5
其中哈希表的负载因子可以通过公式：
负载因子=哈希表已保存节点数量/哈希表大小
load_factory=ht[0].used/ht[0].size

根据BGSAVE命令或BGREWRITEAOF命令是否正在执行，服务器执行扩展操作所需
的负载因子并不相同，这是因为在执行BGSAVE命令或BGREWRITEAOF命令的过程中，Redis
需要创建当前服务器进程的子进程，而大多数操作系统都采用写时复制技术来优化子进程的使用效率
所以在子进程存在期间，服务器会提高执行扩展操作所需的负载因子，从而尽可能避免在子进程存在期间
进行哈希表扩展操作，这可以避免不必要的内存写入操作，最大限度地节约内存

另一个，当哈希表的负载因子小于0.1时，程序自动开始对哈希表执行收缩操作。

5.渐进性rehash

扩展或收缩哈希表需要将ht[0]里面的所有键值对rehash到ht[1]里面，但是，这个rehash
动作并不是一次性、集中式地完成的，而是分多次、渐进式地完成的。

以下是哈希表渐进式rehash的详细步骤:
1>为ht[1]分配空间，让字典同时持有ht[0]和ht[1]两个哈希表
2>在字典中维持一个索引计数器变量rehashidx,并将它的值设置为0，表示rehash工作
正式开始
3>在rehash进行期间，每次对字典执行添加、删除、查找或者更新操作时，程序除了执行
指定的操作以外，还会顺带将ht[0]哈希表在rehashidx索引上的所有键值对rehash到
ht[1],当rehash工作完成之后，程序将rehashidx属性的值增一。
4>随着字典操作的不断执行，最终在某个时间点上，ht[0]的所有键值对都会被rehash到
ht[1]，这时程序将rehashidx属性的值设为-1，表示rehash操作已经完成。



# 4跳跃表
跳跃表(skiplist)是一种有序数据结构，它通过在每个节点中维持多个指向其他节点的指针，从而达到快速访问节点的目的。

跳跃表支持平均O(logN)、最坏O(N)复杂度的节点查找，还可以通过顺序性操作来批量处理节点。

在大部分情况下，跳跃表的效率可以和平衡树相媲美

Redis使用跳跃表作为有序集合键的底层实现之一，如果一个有序集合包含的元素数量表较多，又或者有序集合中元素
的成员是比较长的字符串时，Redis就会使用跳跃表来作为有序集合键的底层实现。

和链表、字典等数据结构被广泛地应用在Redis内部不同，Redis只在两个地方用到了跳跃表，一个是实现有序集合键，另一个
是在集群节点中用作内部数据结构。

跳跃表节点
typedef struct zskiplistNode {
  //层
  struct zskiplistLevel {
    //前进指针
    struct zskiplistNode *forward;
    
    //跨度
    unsigned int span;
  } level[];
  
  //后退指针
  struct zskiplistNode *backward;
  
  //分值
  double score;
  
  //成员对象
  robj *obj;
} zskiplistNode;
跳跃表
typedef struct zskiplist{
  //表头节点和表尾节点
  struct zskiplistNode *header,*tail;
  //表中节点的数量
  ussigned long length;
  
  //表中层数最大的节点的层数
  int level;
}zskiplist;

#5 整数集合
整数集合（intset）是集合键的底层实现之一，当一个集合只包含整数值元素，并且这个集合的元素数量不多时，Redis
就会使用整数集合键的底层实现

整数集合是Redis用于保存整数的集合抽象数据结构，它可以保存类型为int16_t、int32_t或者int64_t的整数值，并且
保证集合中不会出现重复元素

typedef struct intset {
 //编码方式
 uint32_t encoding;
 //集合包含的元素数量
 uint32_t length;
 //保存元素的数组
 int8_t contents[];
}intset;

虽然intset结构将contents属性声明为int8_t类型的数组，但实际上contents数组并不保存任何int8_t类型的值，
contents数组的真正类型取决与encoding属性的值

1 升级
每当我们要将一个新元素添加到整数集合里面，并且新元素的类型比整数集合现有所有元素的类型都要长时，整数集合需要先
进行升级(upgrade),然后才能将新元素添加到整数集合里面。
因为每次向整数集合添加新元素都可能会引起升级，而每次升级都需要对底层数组中已有的所有元素进行类型转换，所以向
整数集合添加新元素的时间复杂度为O(N)
2 升级的好处
1>提升整数集合的灵活性
2>尽可能地节约内存

3.降级
整数集合不支持降级操作，一旦对数组进行了升级，编码就会一直保持升级后的状态。


#6 压缩列表
压缩列表（ziplist）是列表键和哈希键的底层实现之一。
当一个列表键只包含少量列表项，并且每个列表项要么就是小整数，要么就是长度比较短的字符串，那么
Redis就会使用压缩列表来做列表键和的底层实现。
另外，当一个哈希键只包含少量键值对，比且每个键值对的键和值要么就是小整数值，要么就是长度比较短的字符串，
那么Redis就会使用压缩列表来做哈希键的底层实现。
1.压缩列表的够成
压缩列表是Redis为了节约内存而开发的，是由一系列特殊编码的连续内存块组成的顺序型数据结构。一个压缩列表可以包含
任意多个节点，每个节点可以保存一个字节数组或者一个整数值。

zlbytes zltail zllen entry1 entry2 ... entryN zlend
zlbytes:记录整个压缩列表占用的内存字节数
zltail:记录压缩列表表尾节点距离压缩列表的起始地址有多少字节。
zllen:记录了压缩列表包含的节点数量
entryX：列表节点
zlend:特殊值，用于标记压缩列表的末端

压缩列表节点的各个组成部分
previous_entry_length encoding content

连锁更新

#7 对象

前面介绍了Redis用到的所有主要数据结构，比如简单动态字符串（SDS）、双端链表、字典、压缩列表、整数集合
Redis并没有直接使用这些数据结构来实现键值对数据库，而是基于这些数据结构创建了一个对象系统，这个系统包含字符串
对象、列表对象、哈希对象、集合对象和有序集合对象这五种类型的对象。每种对象都用到了至少一种我们前面所介绍的数据结构

1.Redis使用对象来表示数据库中的键和值，每次当我们在Redis的数据库中新创建一个
键值对时，我们至少会创建两个对象，一个对象用作键值对的键，另一个对象用作键
值对的值

Redis中的每个对象都由一个redisObject结构表示，该结构中和保运数据有关的三个属性
分别是type属性、encoding属性和ptr属性
typedef struct redisObject {
    //类型
    unsigned type:4;
    //编码
    unsigned encoding:4;
    //指向底层实现数据结构的指针
    void *ptr;
    //...
    
}robj;

1>type属性可以是一下类型常量中的一个

REDIS_STRING 字符串对象 type命令输出为string
REDIS_LIST 列表对象     type命令输出为list
REDIS_HASH 哈希对象     type命令输出为hash
REDIS_SET 集合对象      type命令输出为set
REDIS_ZSET 有序集合对象  type命令输出为zset

对于Redis数据库保存的键值对来说，键总是一个字符串对象，而值则可以是
字符串对象、列表对象、哈希对象、集合对象或者有序集合对象的其中一种。
因此：
当我们称呼一个数据库键为“字符串键”时，我们指的是“这个数据库键所对应的值为字符串对象”
当我们称呼一个键为“列表键”时，我们指的是“这个数据库键所对应的值为列表对象”
TYPE命令的实现方式也与此类似，当我们对一个数据库键执行TYPE命令时，命令返回
的结果为数据库键对应的值对象的类型，而不是键对象的类型

2>编码和底层实现
对象ptr指针指向对象的底层实现数据结构，而这些数据结构由
对象的encoding属性决定

编码常量                       底层数据结构
REDIS_ENCODING_INT          long类型的正数
REDIS_ENCODING_EMBSTR       embstr编码的简单动态字符串
REDIS_ENCDING_RAW           简单动态字符串
REDIS_ENCODING_HT           字典
REDIS_ENCODING_LINKEDLIST   双端链表
REDIS_ENCODING_ZIPLIST      压缩列表
REDIS_ENCODING_INTSET       正数集合
REDIS_ENCODING_SKIPLIST     跳跃表和字典

每种类型的对象都至少使用两种不同的编码

类型                编码                      对象
REDIS_STRING       REDIS_ENCODING_INT       使用整数值实现的字符串对象
REDIS_STRING       REDIS_ENCODING_EMBSTR    使用embstr编码的简单动态字符串实现的字符串对象
REDIS_STRING       REDIS_ENCODING_RAW       使用简单动态字符串实现的字符串对象
REDIS_LIST         REDIS_ENCODING_ZIPLIST   使用压缩列表实现的列表对象
REDIS_LIST         REDIS_ENCODING_LINKEDLIST使用双端链表实现的列表对象
REDIS_HASH         REDIS_ENCODING_ZIPLIST   使用压缩列表实现的哈希对象
REDIS_HASH         REDIS_ENCODING_HT        使用字典实现的哈希对象
REDIS_SET          REDIS_ENCODING_INTSET    使用整数集合实现的集合对象
REDIS_SET          REDIS_ENCODING_HT        使用字典实现的集合对象
REDIS_ZSET         REDIS_ENCODING_ZIPLIST   使用压缩列表实现的有序集合对象
REDIS_ZSET         REDIS_ENCODING_SKIPLIST  使用跳跃表和字典实现的有序集合对象

使用object encoding对不同编码的输出
对象所使用的底层数据结构             编码常量             OBJECT ENCODING 命令输出
整数                         REDIS_ENCODING_INT      "int"
embstr编码的简单动态字符串(SDS) REDIS_ENCODING_EMBSTR    "embstr"
简单动态字符串                 REDIS_ENCODING_RAW       "raw"
字典                         REDIS_ENCODING_HT         "hashtable"
双端链表                      REDIS_ENCODING_LINKEDLIST “linkedlist”
压缩列表                      REDIS_ENCODING_ZIPLIST    “ziplist”
整数集合                      REDIS_ENCODING_INTSET    "intset"
跳跃表和字典                  REDIS_ENCODING_SKIPLIST   “skiplist”

通过encoding属性来设定对象所使用的编码，而不是为特定类型的对象关联一种
固定编码，极大提升了Redis的灵活性和效率，因为Redis可以根据不同的使用场景来
为一个对象设置不同的编码，从而优化对象在某一场景下的效率。
例子：
在列表对象包含的元素比较少时，Redis使用压缩列表作为列表对象的底层实现：
因为压缩列表比双端链表更节约内存，并且在元素数量较少时，在内存中以连续块方式
保存的压缩列表比起双端链表可以更快被载入到缓存中
随着列表对象包含的元素越来越多，使用压缩列表来保存元素的有事逐渐消失时，对
对象就会将底层实现从压缩列表转向功能更强、也更适合保存大量元素的双端链表上面


2.字符串对象




















































































































































