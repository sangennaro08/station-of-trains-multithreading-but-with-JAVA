import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class StazioneTreni {

    private final Semaphore binari = new Semaphore(Variabili_globali.BINARI_DISP);
    private final ArrayList<Treni> treni = new ArrayList<>();
    private final Map<Integer, Treni> treniInStazione = new HashMap<>();

    private final Object lockScrittura = new Object();
    private final Object lockPriorita = new Object();

    private final ReentrantLock rilascia_corretto_returned_id = new ReentrantLock();

    Condition possible_continue = rilascia_corretto_returned_id.newCondition();
    Condition controllo_priorita = rilascia_corretto_returned_id.newCondition();

    private int returned_id=-1;
    private int controlloTreniInStazione = 0;
    private int treniCompletati = 0;

    public void creaTreni() {
        for (int i = 0; i < Variabili_globali.TRENI_IN_ENTRATA; i++)
            treni.add(new Treni(i, this));
    }

    public void inizializzaTreni() {
        for (Treni t : treni)
            t.start();
    }

    public void simulaTreno(Treni t) {
        synchronized (lockScrittura) {
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

    public boolean possibileEntrataInStazione(Treni t) {

        synchronized (lockPriorita) {
            if((Variabili_globali.PRIORITY_THRESHOLD < t.priorita && controlloTreniInStazione== Variabili_globali.BINARI_DISP) ||
            (Variabili_globali.LIMIT_OF_STARVATION < t.starving && controlloTreniInStazione== Variabili_globali.BINARI_DISP))
            {
                find_first_candidate(t);

                if(this.returned_id !=-1)
                {

                    rilascia_corretto_returned_id.lock();
                    try {
                        if(this.returned_id != -1)
                        {
                            try
                            {
                                possible_continue.await(6,TimeUnit.SECONDS);
                            }catch(InterruptedException e)
                            {
                                Thread.currentThread().interrupt();
                                rilascia_corretto_returned_id.unlock();
                            }
                        }
                    } finally {
                        rilascia_corretto_returned_id.unlock();
                    }
                }
                modify_limit_of_starvation();
            }
        } 
        if(!binari.tryAcquire()) 
        {
            synchronized (lockScrittura) {
                System.out.println("Il treno con ID " + t.ID +
                    " deve fare il giro largo, stazione piena\n\n");

                t.tempo_di_arrivo += 2;
                t.starving += 1;
                t.priorita = t.vagoni * t.tempo_di_arrivo + 2 * t.starving;
            }
            return false;
        }

        synchronized (lockScrittura)
        {
            System.out.println("Il treno con ID " + t.ID +
                " CONTROLLA che un treno con priorità elevata voglia il posto\n\n");
        }

        synchronized (lockPriorita)
        {
            treniInStazione.put(t.ID, t);
            controlloTreniInStazione++;
        }

        if(controll_priorities(t))
        {
            synchronized (lockPriorita)
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

    public void find_first_candidate(Treni t) {

        for (Map.Entry<Integer, Treni> entry : treniInStazione.entrySet()) {
            Treni candidato = entry.getValue();
            if (candidato == null) continue;  // Safety check
            
                if ((candidato.priorita < t.priorita && !candidato.scarica) ||
                    (candidato.starving < t.starving && !candidato.scarica)) {
                    
                    rilascia_corretto_returned_id.lock();
                    try {
                        this.returned_id = entry.getKey();  // entry.getKey() è sempre valido
                        controllo_priorita.signalAll();
                    } finally {
                        rilascia_corretto_returned_id.unlock();
                    }
                    
                    synchronized (lockScrittura) {
                        System.out.println("il treno con ID " + t.ID +
                            " HA PRIORITÀ ELEVATA quindi fa spostare il treno con ID " + entry.getKey() + "\n\n");
                    }
                    return;
                }
        }
    }

    public boolean controll_priorities(Treni t) {

    rilascia_corretto_returned_id.lock();
    try {
        boolean selezionato = controllo_priorita.await(6, TimeUnit.SECONDS);

        if (selezionato && this.returned_id == t.ID) {

            synchronized (lockScrittura) {
                System.out.println("il treno con ID " + t.ID +
                    " VA VIA per lasciare il posto ad un treno con priorità elevata\n\n");
            }

                t.starving++;
                this.returned_id = -1;

                treniInStazione.remove(t.ID);
                binari.release();
                possible_continue.signal();   

            return true;
        }
        return false;

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return true;
    } finally {
        rilascia_corretto_returned_id.unlock();
    }
}

    public void inizioScaricoMerci(Treni t) {

        synchronized (lockPriorita) {
            t.scarica = true;
        }

        synchronized (lockScrittura) {
            System.out.println("Il treno con ID " + t.ID +
                " HA PASSATO IL CONTROLLO, resterà per " +
                t.tempo_in_stazione + " secondi per scaricare le merci\n\n");
        }

        try {
            Thread.sleep(t.tempo_in_stazione * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (lockScrittura) {
            System.out.println("Il treno con ID " + t.ID +
                " HA FINITO di stare in stazione, adesso andrà via\n\n");
        }

        synchronized (lockPriorita) {
            t.scarica = false;
            controlloTreniInStazione--;
            treniCompletati++;
            treniInStazione.remove(t.ID);
            binari.release();
        }

        if (treniCompletati >= Variabili_globali.TRENI_IN_ENTRATA) {
            synchronized (lockScrittura) {
                System.out.println("TUTTI I TRENI HANNO FINITO DI SCARICARE LE MERCI\n\n");
            }
        }
    }

    public void modify_limit_of_starvation()
    {
        double media_starving = 0;

        for(Treni treno : treni)
            media_starving+=treno.starving;

        media_starving /= treni.size();

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
        for (Treni t : treni) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void infoTrains() {
        for (Treni t : treni) {
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
        for(Treni t : treni)media_starving+=t.starving;

        media_starving /= treni.size();

        System.out.println("media punti starving "+media_starving);

    }
}