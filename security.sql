-- MySQL dump 10.13  Distrib 8.3.0, for macos14 (arm64)
--
-- Host: 192.168.5.249    Database: tem
-- ------------------------------------------------------
-- Server version	8.4.3

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
-- auto-generated definition
create table user
(
    id          bigint auto_increment comment 'userID'
        primary key,
    username    varchar(255)  not null comment '用户名',
    password    varchar(255)  not null comment '密码',
    authorities json          null comment '权限',
    status      int default 0 null comment '账户状态 0 启用 1禁用',
    user_id     bigint        not null comment 'userid',
    constraint user_username_id_uindex
        unique (username, id)
)
    charset = utf8mb4;

create index user_user_id_index
    on user (user_id);



