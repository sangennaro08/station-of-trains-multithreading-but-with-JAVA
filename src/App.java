public class App {
    public static void main(String[] args) {

        StazioneTreni stazione = new StazioneTreni();

        stazione.creaTreni();
        stazione.inizializzaTreni();

        stazione.joinThreads();
        stazione.infoTrains();

        stazione.media_starving();
    }
}

