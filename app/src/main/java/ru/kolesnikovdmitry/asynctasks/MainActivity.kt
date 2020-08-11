package ru.kolesnikovdmitry.asynctasks

import android.content.Context
import android.os.Bundle
import android.provider.Contacts
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ActorScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlin.coroutines.CoroutineContext

class MainActivity: AppCompatActivity() {

    var realTime = 1

    @ObsoleteCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar!!.title = "Coroutines"

        //Создаем канал-корутину actor:
        val myActor = GlobalScope.actor<String> {
            while (true) {
                delay(2000)
                val textFromActor = this.channel.receive()
                if(textFromActor.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        textViewMessageFromActor.append(" $textFromActor \n")
                    }
                }
            }
        }

        //Создаем канал для отправки в него сообений и соответствующую сопрограмму:
        val channel = Channel<String>()
        //В этом канале мы будем каждую секунду получать соообщения из канала, и если что-то получим,
        // будем выводить на экран
        val jobChannel = GlobalScope.launch {
            while (true) {
                //каждые 2 секунды
                delay(2000L)
                //считываем сообщения из канала
                val messageFromChanel = channel.receive()
                //если они не пустые
                if (messageFromChanel.isNotEmpty()) {
                    //залазием в главный поток
                    withContext(Dispatchers.Main) {
                        //и добавляем полученное сообщение на главный экран
                        textViewMessageFromChannel.append("$messageFromChanel \n")
                    }
                }
            }
        }

        //со старта программы будем вести отсчет времени со старта программы и выводить его на экран
        val job1 = GlobalScope.launch{
            var time = 0
            while (true) {
                //сразу в главном потоке выводим теккущее время
                //из-за выхода в главный поток, этот поток тоже остановится, если остановится главный поток
                withContext(Dispatchers.Main) {
                    textViewTimer.text = "Прошло $time секунд со старта программы."
                }
                //затем останавливаем на секунду этот поток, после чего увеличиваем счетчик и продолжаем цикл
                delay(1000)
                time += 1
            }
        }

        //А в этом потоке у нас будет просто тикать часики, на экран будем потом выводить
        //Этот поток будет работать, даже если основной поток приостановится
        val job2 = GlobalScope.launch {
            while (true) {
                delay(1000)
                realTime++
            }
        }

        //кнопка для блокировки основного UI потока
        btnBlockUIThread.setOnClickListener {
            var time = editTextBlockUIThread.text.toString()
            if (time.isEmpty()) {
                time = 1L.toString()
            }
            blockUiThread(time.toLong())
        }

        //кнопка для показывание реального времени
        btnRealTime.setOnClickListener {
            textViewRealTimer.text = "Прошло $realTime секунд."
        }

        //кнопка для вычисления числа фибоначи
        btnFibonachi.setOnClickListener {
            textViewFibonachi.text = ""
            val number = editTextFibonachi.text.toString()
            if (number.isEmpty()) {
                Toast.makeText(applicationContext, "Введите число для начала!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            btnFibonachi.visibility = Button.INVISIBLE
            progressBarFibonachi.visibility = ProgressBar.VISIBLE
            val job3 = GlobalScope.async {
                var result = ""
                //запускаем в новом фоновом потоке вычисления
                result = fib(number.toLong()).toString()

                //и по готовности  отправляем в главный поток результат (Выводи на экран)
                withContext(Dispatchers.Main) {
                    textViewFibonachi.text = "Ответ: $result"
                    btnFibonachi.visibility = Button.VISIBLE
                    progressBarFibonachi.visibility = ProgressBar.INVISIBLE
                }

            }
            //Поставим таймер, по истечении которого будет останавливаться вычисление и выводиться,
            // что слишком долго
            it.postDelayed({
                if (!job3.isCompleted) {
                    textViewFibonachi.text = "Too long! This number is too big to calculate it (max is 5 sec)!"
                    job3.cancel()
                    btnFibonachi.visibility = Button.VISIBLE
                    progressBarFibonachi.visibility = ProgressBar.INVISIBLE
                }
            }, 5000)
        }

        //кнопка для отправки сообщения на канал
        btnSendToChannel.setOnClickListener {
            val textFromUser = editTextMessageToChannel.text.toString()
            if (textFromUser.isEmpty()) {
                Toast.makeText(applicationContext, "Enter message firstly!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            editTextMessageToChannel.setText("")
            btnSendToChannel.isEnabled = false
            //Самое главное, что мы не сможем отправить новое сообщение, пока не было получено старое
            GlobalScope.launch {
                channel.send(textFromUser)
                withContext(Dispatchers.Main) {
                    btnSendToChannel.isEnabled = true
                }
            }
        }

        btnSendToActor.setOnClickListener {
            val textToActor = editTextMessageToActor.text.toString()
            if(textToActor.isEmpty()) {
                Toast.makeText(applicationContext, "Empty message!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            editTextMessageToActor.setText("")
            btnSendToActor.isEnabled = false
            GlobalScope.launch {
                myActor.send(textToActor)
                withContext(Dispatchers.Main) {
                    btnSendToActor.isEnabled = true
                }
            }
        }

    }

    // Блокируется весь UI поток на time секунд. Стоит отметить, что кнопки все равно можно нажимать
    // и их нажаитя обработаются после выхода основного потока из спящего режима
    private fun blockUiThread(time : Long) {
        btnBlockUIThread.isEnabled = false
        runBlocking() {
            delay(time)
            btnBlockUIThread.isEnabled = true
        }
    }

    //функция для вычисления числа фибоначи
    private fun fib(n: Long): Long {
        return if (n <= 1) n
        else fib(n - 1) + fib(n - 2)
    }

}