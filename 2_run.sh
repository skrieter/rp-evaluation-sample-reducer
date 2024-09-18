#!/bin/bash

# Settings
config_dir='config'
max_memory='128'

args="-Xmx${max_memory}g -jar build/libs/evaluation-sample-reducer-1.0-all.jar --config_dir ${config_dir} --config"

# Run
java ${args} clean
java ${args} prepare_tp
#java ${args} prepare_industry
java ${args} prepare_gd
java ${args} yasa
java ${args} combine
java ${args} reduce
java ${args} coverage
