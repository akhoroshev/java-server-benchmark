[![Build Status](https://travis-ci.org/akhoroshev/java-server-benchmark.svg?branch=master)](https://travis-ci.org/akhoroshev/java-server-benchmark)

# Server Benchmark

## Сервер

Доступные архитектуры
- NAIVE_BLOCKING - по потоку на каждого клиента
- BLOCKING - на каждого клиента поток на чтение, поток на запись и общий пул потоков для обработки задач
- NON_BLOCKING - входящие сообщения от всех клиентов вычитываются одним потоком selector'ом, затем задачи попадают в пул потоков, после чего, отправляются одним потоком selector'ом на запись
- ASYNC - асинхронный клиент с пулом потоков для обработки задач

Запуск производится командой 
```shell script
./gradlew cli:run
```

Посмотреть помощь
```shell script
./gradlew cli:run --args='--help'
```

## Benchmark GUI

Запуск производится командой
```shell script
./gradlew gui:run
```

Внизу можно выбрать параметры тестирования
![hello](https://user-images.githubusercontent.com/26367308/72550395-b5414600-38a3-11ea-8b83-2b7626db69d6.png)

Доступные параметры для изменения
- M - число клиентов
- N - размер сортируемого массива
- DELTA - интервал между запросами одного клиента
- Request count - количество запросов от каждого клиента

Во вкладке `Settings` можно указать хост адрес и порт
![settings](https://user-images.githubusercontent.com/26367308/72550420-c4c08f00-38a3-11ea-8205-719f291a355f.png)

Результаты отображаются на экране
![results](https://user-images.githubusercontent.com/26367308/72550456-d1dd7e00-38a3-11ea-98e5-17932c05b7fa.png)

Отображаются три метрики

- Request process time on server - время, потраченное сервер на решение конкретной бизнес задачи (в нашем случае квадратична сортировка)
- Client process time on server - время с момента получения сообщения от клиента, до момента отправки
- Server response time - время ожидания клиента ответа от сервера

## Результаты

Некоторые результаты сохранены в папке results, конфигурация сервера - 8xCPU, 16G RAM

