public class Controllo_var_globali {
    public static void controll()
    {
        if(Variabili_globali.BINARI_DISP<=0)
        {   
            Variabili_globali.BINARI_DISP=10;
            System.out.println("binari disponibili sono stati settati a "+Variabili_globali.BINARI_DISP);
        }

        if(Variabili_globali.PRIORITY_THRESHOLD < 40)
        {
            Variabili_globali.PRIORITY_THRESHOLD = 40;
            System.out.println("limite di priorità settato a "+Variabili_globali.PRIORITY_THRESHOLD);
        }

        if(Variabili_globali.LIMIT_OF_STARVATION < 3)
        {
            Variabili_globali.LIMIT_OF_STARVATION = 3;
            System.out.println("il limte di starvation è stato settato a "+Variabili_globali.LIMIT_OF_STARVATION);
        }

        if(Variabili_globali.TRENI_IN_ENTRATA <=0)
        {
            Variabili_globali.TRENI_IN_ENTRATA = 10;
            System.out.println("treni in entrata è stato settato a "+Variabili_globali.TRENI_IN_ENTRATA);
        }

        try
        {
            Thread.sleep(5000);
            
        }catch(InterruptedException e)
        {   
            Thread.currentThread().isInterrupted();
        }
    }
}
