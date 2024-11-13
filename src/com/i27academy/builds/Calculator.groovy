package com.i27academy.builds;

class Calculator {
    def jenkins
    calculator(jenkins) {
        this.jenkins = jenkins
    }

    def add(firstNumber, secondNumber) {
        // logial code base here 
        return firstNumber + secondNumber
    }

    // add(2,3)
    def multiply(firstNumber, secondNumber) {
        // logial code base here 
        return firstNumber * secondNumber
    }   

    // def buildApp() {
    //     jenkins.sh """
    //     mvn package -DskipTests=true
    //     """
    // }
}




// methods 
