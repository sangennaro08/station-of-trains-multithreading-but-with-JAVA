import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class StazioneTreni {

    //----------STRUTTURE DATI UTILIZZATE PER INSERIRE I TRENI ALL'INTERNO---------------//
    private final Semaphore BINARI = new Semaphore(Variabili_globali.BINARI_DISP);
    private final ArrayList<Treni> TRENI = new ArrayList<>();
    private final Map<Integer, Treni> TRENI_IN_STAZIONE = new HashMap<>();

    //----------STRUTTURE DI CONCORRENZA UTILIZZATE PER AVERE MUTUA ESCLUSIONE----------//
    private final Object LOCKSCRITTURA = new Object();
    private final Object LOCKPRIORITA = new Object();

    private final ReentrantLock RILASCIA_CORRETTO_RETURNED_ID = new ReentrantLock();

    Condition possible_continue = RILASCIA_CORRETTO_RETURNED_ID.newCondition();
    Condition controllo_priorita = RILASCIA_CORRETTO_RETURNED_ID.newCondition();

    private int returned_id=-1;
    private int controlloTreniInStazione = 0;
    private int treniCompletati = 0;


    //--------------------INIZIALIZZAZIONE THREAD-TRENI E LANCIO DEI THREAD--------------//
    public void creaTreni() {
        for (int i = 0; i < Variabili_globali.TRENI_IN_ENTRATA; i++)
            TRENI.add(new Treni(i, this));
    }

    public void inizializzaTreni() {
        for (Treni t : TRENI)
            t.start();
    }

    //--------------------SIMULO L'ARRIVO IN STAZIONE DEL TRENO-------------------------//
    public void simulaTreno(Treni t) {
        synchronized (LOCKSCRITTURA) {
            System.out.println(
                "Il treno " + t.ID + " arriverà in " + (t.tempo_di_arrivo + t.tempo_giro_largo) +
                " secondi (" + t.vagoni + " vagoni e priorità " + t.priorita + ")\n\n"
            );
        }

        try {
            Thread.sleep((t.tempo_di_arrivo + t.tempo_giro_largo) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    //-----------------CONTROLLIAMO SE IL TRENO PUò ENTRARE IN STAZIONE-----------------//
    //----return:true=entra in stazione false=non entra in stazione,fa il giro largo----//
    public boolean possibileEntrataInStazione(Treni t) {

        synchronized (LOCKPRIORITA) {
            //if((Variabili_globali.PRIORITY_THRESHOLD < t.priorita && controlloTreniInStazione== Variabili_globali.BINARI_DISP) ||
            //(Variabili_globali.LIMIT_OF_STARVATION < t.starving && controlloTreniInStazione== Variabili_globali.BINARI_DISP))
            if((t.priorita > Variabili_globali.PRIORITY_THRESHOLD || t.starving > Variabili_globali.LIMIT_OF_STARVATION) &&
                (controlloTreniInStazione == Variabili_globali.BINARI_DISP))
            {
                find_first_candidate(t);

                if(this.returned_id !=-1)set_returned_id(t);

                modify_limit_of_starvation();
            }
        } 
        if(!BINARI.tryAcquire()) 
        {
            synchronized (LOCKSCRITTURA) {
                System.out.println("Il treno con ID " + t.ID +
                    " deve fare il giro largo, stazione piena\n\n");

                t.tempo_di_arrivo += 2;
                t.starving += 1;
                t.priorita = t.vagoni * t.tempo_di_arrivo + 2 * t.starving;
            }
            return false;
        }

        synchronized (LOCKSCRITTURA)
        {
            System.out.println("Il treno con ID " + t.ID +
                " CONTROLLA che un treno con priorità elevata voglia il posto\n\n");
        }

        synchronized (LOCKSCRITTURA)
        {
            TRENI_IN_STAZIONE.put(t.ID, t);
            controlloTreniInStazione++;
        }

        if(controll_priorities(t))
        {
            synchronized (LOCKPRIORITA)
            {   
                t.scarica=false;
                controlloTreniInStazione--;
                t.tempo_giro_largo+=2;
                t.priorita = t.vagoni * t.tempo_di_arrivo + 4*t.starving;    
            }
            return false;
        }

        return true;
    }

    //------TROVA IL PRIMO CANDIDATO ACCETTABILE DA CACCIARE----------//
    public void find_first_candidate(Treni t) {

        for (Map.Entry<Integer, Treni> entry : TRENI_IN_STAZIONE.entrySet()) {
            Treni candidato = entry.getValue();
            if (candidato == null) continue;  // Safety check
            
                //if ((candidato.priorita < t.priorita && !candidato.scarica && !candidato.entrata_di_priorita) ||
                //    (candidato.starving < t.starving && !candidato.scarica && !candidato.entrata_di_priorita)) 
                if((candidato.priorita < t.priorita || candidato.starving < t.starving) && (!candidato.scarica && !candidato.entrata_di_priorita))
                {
                    
                    RILASCIA_CORRETTO_RETURNED_ID.lock();
                    try {
                        this.returned_id = entry.getKey();  // entry.getKey() è sempre valido
                        controllo_priorita.signalAll();
                    } finally {
                        RILASCIA_CORRETTO_RETURNED_ID.unlock();
                    }
                    
                    synchronized (LOCKSCRITTURA) {
                        System.out.println("il treno con ID " + t.ID +
                            " HA PRIORITÀ ELEVATA quindi fa spostare il treno con ID " + entry.getKey() + "\n\n");
                    }
                    return;
                }
        }
    }

    //------IL TRENO IN STAZIONE CONTROLLA SE è LUI QUELLO CACCIATO O MENO-----------------//
    public boolean controll_priorities(Treni t) {

    RILASCIA_CORRETTO_RETURNED_ID.lock();
    try {
        boolean selezionato = controllo_priorita.await(6, TimeUnit.SECONDS);

        if (selezionato && this.returned_id == t.ID) {

            synchronized (LOCKSCRITTURA) {
                System.out.println("il treno con ID " + t.ID +
                    " VA VIA per lasciare il posto ad un treno con priorità elevata\n\n");
            }

                t.starving++;
                this.returned_id = -1;

                TRENI_IN_STAZIONE.remove(t.ID);
                BINARI.release();
                possible_continue.signal();   

            return true;
        }
        return false;

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return true;
    } finally {
        RILASCIA_CORRETTO_RETURNED_ID.unlock();
    }
}

    //SIMULAZIONE DELLO SCARICO MERCI DEL TRENO
    public void inizioScaricoMerci(Treni t) {

        synchronized (LOCKPRIORITA) {
            t.scarica = true;
        }

        synchronized (LOCKSCRITTURA) {
            System.out.println("Il treno con ID " + t.ID +
                " HA PASSATO IL CONTROLLO, resterà per " +
                t.tempo_in_stazione + " secondi per scaricare le merci\n\n");
        }

        try {
            Thread.sleep(t.tempo_in_stazione * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (LOCKSCRITTURA) {
            System.out.println("Il treno con ID " + t.ID +
                " HA FINITO di stare in stazione, adesso andrà via\n\n");
        }

        synchronized (LOCKPRIORITA) {
            t.scarica = false;
            t.entrata_di_priorita = false;
            controlloTreniInStazione--;
            treniCompletati++;
            TRENI_IN_STAZIONE.remove(t.ID);
            BINARI.release();
        }

        if (treniCompletati >= Variabili_globali.TRENI_IN_ENTRATA) {
            synchronized (LOCKSCRITTURA) {
                System.out.println("TUTTI I TRENI HANNO FINITO DI SCARICARE LE MERCI\n\n");
            }
        }
    }

    public void set_returned_id(Treni t)
    {
        RILASCIA_CORRETTO_RETURNED_ID.lock();
        try {
            if(this.returned_id != -1)
            {   
                t.entrata_di_priorita = true;
                try
                {
                    possible_continue.await(6,TimeUnit.SECONDS);

                }catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    RILASCIA_CORRETTO_RETURNED_ID.unlock();
                }
            }
        } finally {
            RILASCIA_CORRETTO_RETURNED_ID.unlock();
        }
    }

    public void modify_limit_of_starvation()
    {
        double media_starving = 0;

        for(Treni treno : TRENI)
            media_starving+=treno.starving;

        media_starving /= TRENI.size();

        if(media_starving > Variabili_globali.LIMIT_OF_STARVATION)
        {
            double occ = controlloTreniInStazione / Variabili_globali.BINARI_DISP;
            double sat = media_starving / (1.0 + media_starving);
            double factor = 1.0 + 0.15 * occ * sat;

            int new_limit = Math.max((int)Variabili_globali.LIMIT_OF_STARVATION , (int)Math.floor(Variabili_globali.LIMIT_OF_STARVATION * factor));
            Variabili_globali.LIMIT_OF_STARVATION = new_limit;
            Variabili_globali.PRIORITY_THRESHOLD = Math.min(Variabili_globali.PRIORITY_THRESHOLD * 1.01,1000.0);
        }
    }

    public void joinThreads() {
        for (Treni t :TRENI) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void infoTrains() {
        for (Treni t : TRENI) {
            System.out.println("treno ID " + t.ID);
            System.out.println("vagoni " + t.vagoni);
            System.out.println("tempo di arrivo " + t.tempo_di_arrivo +
                " e tempo in stazione " + t.tempo_in_stazione);
            System.out.println("priorità finale " + t.priorita);
            System.out.println("punti di starvation accumulati " + t.starving);
            System.out.println("----------------------------------------\n\n\n");
        }
    }

    public void media_starving()
    {
        double media_starving=0;
        for(Treni t : TRENI)media_starving+=t.starving;

        media_starving /= TRENI.size();

        System.out.println("media punti starving "+media_starving);

    }
}