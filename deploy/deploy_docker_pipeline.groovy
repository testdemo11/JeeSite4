pipeline {
    agent {
        label 'linux_root'
    }

    environment {
        docker_image_name = 'jeesite4'
        docker_container_name = 'iJeesite4'
        mysql_docker_ip = '192.168.42.130'
        mysql_port = '3306'
        mysql_user = 'root'
        mysql_pwd = '12345678'
    }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Git branch')
        //string(name: 'mysql_docker_ip', defaultValue: '192.168.42.130', description: 'ip')
        //string(name: 'mysql_port', defaultValue: '3306', description: 'port')
        //string(name: 'mysql_user', defaultValue: 'root', description: 'user')
        //string(name: 'mysql_pwd', defaultValue: '12345678', description: 'password')
    }

    stages{
        stage('同步源码') {
            steps {
                git url:'git@github.com:testdemo11/JeeSite4.git', branch:"$params.branch"
            }
        }

        stage('设定配置文件'){
            steps{
                sh '''
                    source /etc/profile
            
                    export os_type=`uname`
                    cd ${WORKSPACE}/web/bin/docker
                    if [[ "${os_type}" == "Darwin" ]]; then
                        sed -i "" "s/mysql_ip/${mysql_docker_ip}/g" application-prod.yml
                        sed -i "" "s/mysql_port/${mysql_port}/g" application-prod.yml
                        sed -i "" "s/mysql_user/${mysql_user}/g" application-prod.yml
                        sed -i "" "s/mysql_pwd/${mysql_pwd}/g" application-prod.yml
                    else
                        sed -i "s/mysql_ip/${mysql_docker_ip}/g" application-prod.yml
                        sed -i "s/mysql_port/${mysql_port}/g" application-prod.yml
                        sed -i "s/mysql_user/${mysql_user}/g" application-prod.yml
                        sed -i "s/mysql_pwd/${mysql_pwd}/g" application-prod.yml
                    fi
                '''
            }
        }

        stage('Maven 编译'){
            steps {
                sh '''
                    source /etc/profile
                    
                    cd ${WORKSPACE}/root
                    mvn clean install -Dmaven.test.skip=true
                    
                    cd ${WORKSPACE}/web
                    mvn clean package spring-boot:repackage -Dmaven.test.skip=true -U
                '''
            }
        }

        stage('停止 / 删除 现有Docker Container/Image '){
            steps {
                script{
                    try{
                        sh 'docker stop $docker_container_name'
                    }catch(exc){
                        echo 'The container $docker_container_name does not exist'
                    }

                    try{
                        sh 'docker rm $docker_container_name'
                    }catch(exc){
                        echo 'The container $docker_container_name does not exist'
                    }

                    try{
                        sh 'docker rmi $docker_image_name'
                    }catch(exc){
                        echo 'The docker image $docker_image_name does not exist'
                    }
                }
            }
        }

        stage('生成新的Docker Image'){
            steps {
                sh '''
                    cd ${WORKSPACE}/web/bin/docker
                    rm -f web.war
                    cp ${WORKSPACE}/web/target/web.war .
                    docker build -t $docker_image_name .
                '''
            }
        }

        stage('启动新Docker实例'){
            steps {
                sh '''
                    docker run -d --name $docker_container_name -p 8981:8980 $docker_image_name
                '''
            }
        }
    }
}
